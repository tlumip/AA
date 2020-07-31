package com.hbaspecto.pecas.sd.estimation;

@SuppressWarnings("serial")
public class IllegalTargetType extends RuntimeException {

    public final int rowNumber;
    public final String illegalType;

    public IllegalTargetType(int rowNumber, String illegalType) {
        super(illegalType);
        this.rowNumber = rowNumber;
        this.illegalType = illegalType;
    }

    public IllegalTargetType(int rowNumber, String illegalType,
            Throwable cause) {
        super(illegalType, cause);
        this.rowNumber = rowNumber;
        this.illegalType = illegalType;
    }
}
