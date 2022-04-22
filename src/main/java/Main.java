import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        KeepOrder totalOrder = new KeepOrder(); //cria um objeto que ao inicializar irá criar as threads dos processos
        totalOrder.init();


        if(totalOrder.connectionThread != null && totalOrder.orderThread != null && totalOrder.ackThread != null){

            Scanner in = new Scanner(System.in);

            while(true){
                totalOrder.Send(in.next());
            }
        }

    }
}
