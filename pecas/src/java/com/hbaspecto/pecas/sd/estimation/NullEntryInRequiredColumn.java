package com.hbaspecto.pecas.sd.estimation;

@SuppressWarnings("serial")
public class NullEntryInRequiredColumn extends RuntimeException {

    public final Object nullEntry;
    public final String columnName;

    public NullEntryInRequiredColumn(Object nullEntry, String columnName) {
        super(nullEntry + " in column " + columnName);
        this.nullEntry = nullEntry;
        this.columnName = columnName;
    }

    public NullEntryInRequiredColumn(Object nullEntry, String columnName,
            Throwable cause) {
        super(nullEntry + " in column " + columnName, cause);
        this.nullEntry = nullEntry;
        this.columnName = columnName;
    }
}
