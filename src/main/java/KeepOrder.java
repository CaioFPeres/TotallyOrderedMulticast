import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class KeepOrder {

    public static final String process_id = "P2";   //id do processo que é alterado em cada vez da compilação para identificar qual processo é igualmente ao pid que é seu id
    public static final int pid = 2;
    private static ServerSocket server_socket;    //inicia o socket e pega o endereço ip
    private static InetAddress ServerIP = InetAddress.getLoopbackAddress();

    private static int[] PortList = new int[]{3215,4120,4500};  //vetor com as portas que os processos ocorrem
    private static final int NUM_PROCESSES = PortList.length;    //numero de processos ocorrendo
    private static volatile ArrayList<PEvent> PEventList = new ArrayList<>();       //vetor dos processos e eventos
    private static volatile HashMap<String, HashSet<String>> AckBuffer = new HashMap<>();       // hash map do buffer de acks
    private static int RelogioLogico = 0;  //relogio logico 
    private static int EventsDelivered = 0; // eventos entregues

    public Thread connectionThread = null;  //threads de conexão, ordem e ack
    public Thread orderThread = null;
    public Thread ackThread = null;


    public void init() { //função de inicio que inicializa as threads

        System.out.println("Process: " + process_id);

        // Thread de conexao
        connectionThread = (new Thread() {
            @Override
            public void run(){
                ConnectionListener();
                return;
            }
        });

        connectionThread.start();

        // Thread de ordem
        orderThread = (new Thread() {
            @Override
            public void run(){
                GerenciaLista();
                return;
            }
        });

        orderThread.start();

        // Thread de Ack
        ackThread = (new Thread() {
            @Override
            public void run(){
                GerenciaAck();
                return;
            }
        });

        ackThread.start();


        try {
            // aguardar todos os processos iniciarem
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }



    public void ConnectionListener() {  // função que inicia a conexão usando a porta de saida e ficando em um loop até que todos eventos sejam entregues

        int server_port = PortList[pid];

        try {
            server_socket = new ServerSocket(server_port, 0, ServerIP);

            while(true) {

                Socket clientSocket = server_socket.accept();  //aguarda uma conexão de cliente
                ObjectInputStream ObjIS = new ObjectInputStream(clientSocket.getInputStream());  //cria uma stream e le o que o cliente enviou e fecha esse socket
                PEvent event = (PEvent)ObjIS.readObject();
                clientSocket.close();

                (new Thread(){ //nova thread para gerenciar o buffer de eventos
                    @Override
                    public void run(){
                        GerenciaBuffer(event);
                        return;
                    }
                }).start();
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public synchronized void GerenciaBuffer(PEvent event) {   //função que gerencia o buffer de acks

        try {       //try catch para pegar eventuais erros
            if(event.isAck()) {    //se o evento é de ACK verifica-se primeiro se o buffer tem um evento do mesmo tipo do evento recebido
                if(AckBuffer.containsKey(event.getEventID())) {
                    AckBuffer.get(event.getEventID()).add(event.getProcess_id()); // adiciona-se o processo do evento recebido ( note que estamos adicionando no hashSet)

                }else {     //caso contrario se não possua a chave adiciona ao buffer o id do evento e o id do processo
                    HashSet<String> recProcSet = new HashSet<>();
                    recProcSet.add(event.getProcess_id());
                    AckBuffer.put(event.getEventID(), recProcSet);
                }

                synchronized(orderThread) {
                    orderThread.notify();
                }
            }else{      //caso não seja um evento de ACK, adicionamos na lista de eventos e ordenamos.

                PEventList.add(event);
                Collections.sort(PEventList, comp);

                synchronized(ackThread) {
                    ackThread.notify();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void GerenciaLista() {       //função que gerencia a lista de eventos

        while(true) {

            synchronized(orderThread) {
                try {
                    orderThread.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(!PEventList.isEmpty()) {     //se a lista de eventos não estiver vazia pega o primeiro da fila

                PEvent event = PEventList.get(0);

                if(AckBuffer.containsKey(event.getEventID())) {     //verifica se o id do evento existe no buffer de ACK
                    if(AckBuffer.get(event.getEventID()).size() == NUM_PROCESSES) { //caso exista verifica se o numero de acks é o mesmo do numero de processos

                        try{

                            // evento foi recebido por todos os processos
                            System.out.println("Recebido: " + process_id + ":" + event.getProcess_id() + "." + event.getEventID() + " Relogio: " + RelogioLogico);
                            EventsDelivered += 1;

                            PEventList.remove(0);

                        }
                        catch(Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }


    public void GerenciaAck() {     //função que verifica se os acks foram recebidos corretamente

        while(true) {

            synchronized(ackThread) {
                try {
                    ackThread.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(!PEventList.isEmpty()) {  // se a lista de eventos não estiver vazia pega o primeiro da fila

                PEvent event = PEventList.get(0);

                // checa se é possivel enviar o ACK
                if(CheckIntegrity(event)) {

                    PEvent ev = new PEvent();   //cria o evento e incializa as variaveis corretamente

                    ev.setAck(true);
                    ev.setEventID(event.getEventID());
                    ev.setRelogioLogico(RelogioLogico);
                    ev.setPid(pid);
                    ev.setProcess_id(process_id);

                    // envia evento de ACK
                    EnviaEvento(ev);

                    if(AckBuffer.containsKey(event.getEventID())) {     //se tiver uma chave adiciona o processo ao buffer
                        AckBuffer.get(event.getEventID()).add(process_id);
                    }else{      //caso contrario insere uma nova chave contendo o evento e o id do processo
                        HashSet<String> sender_set = new HashSet<>();
                        sender_set.add(process_id);
                        AckBuffer.put(event.getEventID(), sender_set);
                    }
                }
            }
        }
    }


    public void Send(String msg){

        //cria o evento, deixa as variaveis inicializadas com os valores base, tal como ack falso, o relogio logico com valor 0 e id do processo
        PEvent ev = new PEvent();
        ev.setAck(false);
        ev.setEventID(msg);
        ev.setRelogioLogico(RelogioLogico);
        ev.setPid(pid);
        ev.setProcess_id(process_id);

        // Enviar para todos o evento criado
        EnviaEvento(ev);
    }


    public void EnviaEvento(PEvent event) {   //funcção que envia o evento para os outros clientes

        try {

            RelogioLogico = RelogioLogico + 1;  //aumenta o relogio logico

            event.setRelogioLogico(RelogioLogico);   //envia o relogio logico junto com o evento marcando o momento de envio
            Socket socket;
            ObjectOutputStream ObjOS;  //cria um socket e um objeto de saida

            for(int i = 0; i < PortList.length; i++){   //envia para todos os processos do sistema

                socket = new Socket(ServerIP, PortList[i]);         //inicializa o socket
                ObjOS = new ObjectOutputStream(socket.getOutputStream());   //direciona a saída do ObjOS para o socket
                ObjOS.writeObject(event); // envia o evento

                ObjOS.close();      //fecha o output e o socket
                socket.close();

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public boolean CheckIntegrity(PEvent event) {  // função para fazer verificações antes de enviar ack

        if(!AckBuffer.isEmpty() && AckBuffer.containsKey(event.getEventID())) {  //verdadeiro caso o buffer não esteja vazio e possua pelo menos uma entrada do mesmo tipo do evento recebido
            if(AckBuffer.get(event.getEventID()).contains(process_id)) {  // retorna falso caso seja o que enviou
                return false;
            }
        }

        if(event.getPid() == pid) {     //se o id do processo for o mesmo do processo que esta lendo retorna true
            return true;
        }

        if(RelogioLogico == event.getRelogioLogico()) {     //se o relogio logico do evento e do processo atual for igual
            if(pid > event.getPid()) {      //se o id do processo atual for maior que do evento retorna true
                return true;
            }
        }else {         //caso contrario se o relogio atual for maior retorna true, se for menor, atualizamos o relógio pelo o do evento
            if(RelogioLogico > event.getRelogioLogico()){
                return true;
            }else {
                RelogioLogico = event.getRelogioLogico();
                return true;
            }

        }

        return false;
    }


    public static Comparator<PEvent> comp = (event1, event2) -> {       // função de ordenação personalizada

        if(event1.getRelogioLogico() != event2.getRelogioLogico()){     //se o relogio logico do evento 1 e 2 for diferente e do evento 1 for maior retorna 1
            if(event1.getRelogioLogico() > event2.getRelogioLogico()){
                return 1;
            }
        }else{      //caso contrario se forrem iguais e o id do processo 1 for maior que o dois retorna 1
            if(event1.getPid() > event2.getPid()){
                return 1;
            }
        }

        return -1;
    };

}