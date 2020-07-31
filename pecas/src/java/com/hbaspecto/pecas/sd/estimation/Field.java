package com.hbaspecto.pecas.sd.estimation;

public class Field {
    private String name;
    private String defaultValue;
    
    private Field(String name, String defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }
    
    public String defaultValue() {
        return defaultValue;
    }
    
    public static Field string(String name) {
        return new Field(name, "None");
    }
    
    public static Field number(String name) {
        return new Field(name, "0");
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (! (o instanceof Field)) {
            return false;
        } else {
            Field f = (Field) o;
            return this.name.equals(f.name) && this.defaultValue == f.defaultValue;
        }
    }
    
    @Override
    public int hashCode() {
        int result = 17;
        result = result * 31 + name.hashCode();
        result = result * 31 + defaultValue.hashCode();
        return result;
    }

    public String getName() {
        return name;
    }
}
