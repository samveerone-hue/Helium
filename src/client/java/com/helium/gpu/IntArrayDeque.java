package com.helium.gpu;

import java.util.OptionalInt;

public class IntArrayDeque {

    private int[] data;
    private int head;
    private int tail;
    private int size;

    public IntArrayDeque(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be >= 0");
        this.data = new int[Math.max(1, capacity)];
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    public void addlast(int value) {
        this.ensurecapacity(this.size + 1);
        this.data[this.tail] = value;
        this.tail = (this.tail + 1) % this.data.length;
        this.size++;
    }

    public void addfirst(int value) {
        this.ensurecapacity(this.size + 1);
        this.head = (this.head - 1 + this.data.length) % this.data.length;
        this.data[this.head] = value;
        this.size++;
    }

    public OptionalInt poll() {
        if (this.size == 0) {
            return OptionalInt.empty();
        }
        int val = this.data[this.head];
        this.head = (this.head + 1) % this.data.length;
        this.size--;
        return OptionalInt.of(val);
    }

    public int size() {
        return this.size;
    }

    public boolean isempty() {
        return this.size == 0;
    }

    private void ensurecapacity(int minCapacity) {
        if (this.data.length >= minCapacity) return;
        int newCap = Math.max(this.data.length * 2, minCapacity);
        int[] newData = new int[newCap];
        for (int i = 0; i < this.size; i++) {
            newData[i] = this.data[(this.head + i) % this.data.length];
        }
        this.data = newData;
        this.head = 0;
        this.tail = this.size;
    }
}
