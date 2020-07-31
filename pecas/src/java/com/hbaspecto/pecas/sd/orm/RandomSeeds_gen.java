package com.hbaspecto.pecas.sd.orm;

import simpleorm.dataset.SFieldFlags;
import simpleorm.dataset.SFieldInteger;
import simpleorm.dataset.SFieldLong;
import simpleorm.dataset.SRecordInstance;
import simpleorm.dataset.SRecordMeta;
import simpleorm.sessionjdbc.SSessionJdbc;

@SuppressWarnings("serial")
public class RandomSeeds_gen extends SRecordInstance {
    public static final SRecordMeta<RandomSeeds> meta = new SRecordMeta<RandomSeeds>(
            RandomSeeds.class, "random_seeds");

    // Columns in table
    public static final SFieldInteger RandomSeedIndex = new SFieldInteger(meta,
            "random_seed_index", new SFieldFlags[] {SFieldFlags.SPRIMARY_KEY,
                    SFieldFlags.SMANDATORY});

    public static final SFieldLong RandomSeed = new SFieldLong(meta, "random_seed");

    // Column getters and setters
    public long get_RandomSeedIndex() {
        return getLong(RandomSeedIndex);
    }

    public void set_RandomSeedIndex(int value) {
        setLong(RandomSeedIndex, value);
    }

    public long get_RandomSeed() {
        return getLong(RandomSeed);
    }

    public void set_RandomSeed(long value) {
        setLong(RandomSeed, value);
    }

    // Find and create
    public static RandomSeeds findOrCreate(SSessionJdbc ses,
            int _RandomSeedIndex) {
        return ses.findOrCreate(meta, new Object[] {new Integer(_RandomSeedIndex)});
    }

    public SRecordMeta<RandomSeeds> getMeta() {
        return meta;
    }
}
