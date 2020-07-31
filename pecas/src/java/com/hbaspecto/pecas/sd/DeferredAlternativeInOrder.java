package com.hbaspecto.pecas.sd;

import java.util.ArrayList;
import java.util.List;

import com.hbaspecto.pecas.land.Tazs;

/**
 * A DeferredAlternative wrapper that doubles as a linked list node, maintaining
 * a reference to the next DeferredAlternative in a sequence. A
 * DeferredAlternative is "exhausted" if there are no alternatives left that
 * contribute to constraints. Any alternatives further along the list are
 * unconstrained. The absolute end of the list (including unconstrained
 * alternatives) can be detected by checking next() for null.
 */
public class DeferredAlternativeInOrder implements DeferredAlternative {
    private DeferredAlternative inner;
    private DeferredAlternativeInOrder next;
    private boolean unconstrained = false;

    public DeferredAlternativeInOrder(DeferredAlternative inner) {
        this.inner = inner;
    }

    public DeferredAlternativeInOrder next() {
        return next;
    }

    public void setNext(DeferredAlternativeInOrder next) {
        this.next = next;
    }

    public void setNext(DeferredAlternative next) {
        this.next = new DeferredAlternativeInOrder(next);
    }

    @Override
    public void doDevelopment() {
        inner.doDevelopment();
    }

    public boolean isUnconstrained() {
        return unconstrained;
    }

    /**
     * Marks this alternative as unconstrained, moving it to the end of the list
     * (after the exhaustion point). Returns a reference to the new head of the
     * list.
     */
    public DeferredAlternativeInOrder markUnconstrained() {
        unconstrained = true;
        if (next == null) {
            return this;
        } else {
            DeferredAlternativeInOrder newHead = next;
            DeferredAlternativeInOrder cur = this;
            int i = 0;
            while (cur.next != null) {
                if (i > 100) {
                    System.out.println("Why is this looping!?");
                }
                cur = cur.next;
            }
            next = null;
            cur.next = this;
            return newHead;
        }
    }

    @Override
    public double probability() {
        return inner.probability();
    }
    
    @Override
    public int priority() {
        return inner.priority();
    }

    @Override
    public SpaceTypesI activeType() {
        return inner.activeType();
    }

    @Override
    public boolean isConstruction() {
        return inner.isConstruction();
    }

    @Override
    public boolean isRenovation() {
        return inner.isRenovation();
    }

    @Override
    public double amount() {
        return inner.amount();
    }

    @Override
    public boolean tryForceAmount(double amount) {
        return inner.tryForceAmount(amount);
    }

    @Override
    public long parcelNum() {
        return inner.parcelNum();
    }
    
    @Override
    public Tazs taz() {
        return inner.taz();
    }

    @Override
    public String toString() {
        List<String> normalBits = new ArrayList<>();
        List<String> unconstrainedBits = new ArrayList<>();
        DeferredAlternativeInOrder cur = this;
        while (cur != null) {
            String bit = cur.inner.toString();
            if (cur.unconstrained) {
                unconstrainedBits.add(bit);
            } else {
                normalBits.add(bit);
            }
            cur = cur.next;
        }
        return String.join(" -> ", normalBits) + " -|~> "
                + String.join(" ~> ", unconstrainedBits);
    }
}
