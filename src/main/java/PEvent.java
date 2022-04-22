import java.io.Serializable;

public class PEvent implements Serializable {   //classe para implementar um evento, utilizando a interface Serializable

    private int RelogioLogico;  //variaveis que salvam o relogio logico, id do processo, ack, id de eventos
    private int pid;
    private boolean ack = false;
    private String process_id;
    private String EventID;

    public int getPid() {   //metodos setters e getters dessas variaveis 
        return pid;
    }
    public void setPid(int pid) {
        this.pid = pid;
    }

    public String getProcess_id() {
        return process_id;
    }
    public void setProcess_id(String process_id) {
        this.process_id = process_id;
    }

    public int getRelogioLogico() {
        return RelogioLogico;
    }
    public void setRelogioLogico(int relogioLogico) {
        this.RelogioLogico = relogioLogico;
    }

    public String getEventID() {
        return EventID;
    }
    public void setEventID(String eventID) {
        this.EventID = eventID;
    }

    public boolean isAck() {
        return ack;
    }
    public void setAck(boolean ack) {
        this.ack = ack;
    }

}
