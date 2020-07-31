package com.hbaspecto.pecas;

/**
 * A simple container for an integer value, with the subclass used giving
 * compile-time information about its intended purpose. Using these in place of
 * raw integers helps avoid e.g. passing a zone number to a method that requires
 * an activity number.
 * 
 * @author Graham Hill
 * 
 */
public abstract class TaggedIntBox implements Comparable<TaggedIntBox> {
    public final int value;

    public TaggedIntBox(int value) {
        this.value = value;
    }

    public boolean equals(Object other) {
        if (other == this)
            return true;
        if (!(other instanceof TaggedIntBox))
            return false;
        return this.value == ((TaggedIntBox) other).value;
    }

    public int hashCode() {
        return value;
    }

    public String toString() {
        return Integer.toString(value);
    }
    
    public int compareTo(TaggedIntBox other) {
        return Integer.compare(value, other.value);
    }
}