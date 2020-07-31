package com.hbaspecto.pecas.sd.estimation;

@SuppressWarnings("serial")
public class UndefinedGroup extends RuntimeException {
    
    public final int rowNumber;
    public final String groupType;
    public final int undefinedGroupNumber;
    
    public UndefinedGroup(int rowNumber, String groupType, int groupNumber) {
        super(String.valueOf(groupNumber));
        this.rowNumber = rowNumber;
        this.groupType = groupType;
        this.undefinedGroupNumber = groupNumber;
    }
    
    public UndefinedGroup(int rowNumber, String groupType, int groupNumber, Throwable cause) {
        super(String.valueOf(groupNumber), cause);
        this.rowNumber = rowNumber;
        this.groupType = groupType;
        this.undefinedGroupNumber = groupNumber;
    }
}
