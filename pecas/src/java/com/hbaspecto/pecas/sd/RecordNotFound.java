package com.hbaspecto.pecas.sd;

@SuppressWarnings("serial")
public class RecordNotFound extends RuntimeException {
    
    public final Object missingKey;
    
    public RecordNotFound(Object missingKey) {
        super("Record not found "+String.valueOf(missingKey));
        this.missingKey = missingKey;
    }
    
    public RecordNotFound(Object missingKey, Throwable cause) {
        super("Record not found for key "+String.valueOf(missingKey), cause);
        this.missingKey = missingKey;
    }
}
