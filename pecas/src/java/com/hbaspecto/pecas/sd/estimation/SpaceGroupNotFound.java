package com.hbaspecto.pecas.sd.estimation;

@SuppressWarnings("serial")
public class SpaceGroupNotFound extends RuntimeException {

    public final int rowNumber;
    public final String missingName;

    public SpaceGroupNotFound(int rowNumber, String missingName) {
        super(missingName);
        this.rowNumber = rowNumber;
        this.missingName = missingName;
    }

    public SpaceGroupNotFound(int rowNumber, String missingName,
            Throwable cause) {
        super(missingName, cause);
        this.rowNumber = rowNumber;
        this.missingName = missingName;
    }
}
