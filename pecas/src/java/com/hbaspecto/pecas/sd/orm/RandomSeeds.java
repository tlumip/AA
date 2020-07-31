package com.hbaspecto.pecas.sd.orm;

import java.util.ResourceBundle;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.pb.common.util.ResourceUtil;

import simpleorm.sessionjdbc.SSessionJdbc;

@SuppressWarnings("serial")
public class RandomSeeds extends RandomSeeds_gen {
    private static HashFunction hashfun;

    public static void init(ResourceBundle rb) {
        int seed = ResourceUtil.getIntegerProperty(rb, "PredefinedRandomNumberSeed");
        hashfun = Hashing.murmur3_128(seed);
    }

    /**
     * Retrieves the random seed that matches the specified situation, as
     * described by a series of long integers. If some elements of the situation
     * are not integers, pass in a hash value instead.
     */
    public static long getRandomSeed(SSessionJdbc session,
            long... situation) {
        int situationHash = hash(situation).asInt();
        int front = situationHash >>> 16;
        int back = situationHash & 0x0000ffff;
        RandomSeeds record = session.mustFind(meta, back);
        return hash(front, record.get_RandomSeed()).asLong();
    }

    private static HashCode hash(long... inputs) {
        Hasher hasher = hashfun.newHasher();
        for (long input : inputs) {
            hasher.putLong(input);
        }
        return hasher.hash();
    }
}
