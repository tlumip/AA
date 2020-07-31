package com.hbaspecto.pecas.sd;

import java.util.Random;

public class RandomRandomStream implements RepeatableRandomStream {
    private Random random;
    
    public RandomRandomStream(Random random) {
        this.random = random;
    }

    @Override
    public double next() {
        return random.nextDouble();
    }
}
