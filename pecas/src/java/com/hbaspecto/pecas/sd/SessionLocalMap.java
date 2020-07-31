package com.hbaspecto.pecas.sd;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import simpleorm.dataset.SRecordInstance;
import simpleorm.sessionjdbc.SSessionJdbc;

public abstract class SessionLocalMap<K, R extends SRecordInstance> {
    private Map<SSessionJdbc, Map<K, Optional<R>>> cache = new WeakHashMap<>();
    private Map<SSessionJdbc, Boolean> cachedSessions = new WeakHashMap<>();

    private Map<K, Optional<R>> getSessionLocalMap(SSessionJdbc session) {
        if (!cache.containsKey(session)) {
            cache.put(session, new HashMap<>());
        }
        return cache.get(session);
    }

    public synchronized R getRecord(K key) {
        Optional<R> record = getRecordIfExists(key);
        if (!record.isPresent()) {
            throw new RecordNotFound(key);
        }
        return record.get();
    }

    public synchronized Optional<R> getRecordIfExists(K key) {
        SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
        Map<K, Optional<R>> map = getSessionLocalMap(session);
        Optional<R> result = map.computeIfAbsent(key, k -> {
            boolean wasBegun = true;
            if (!session.hasBegun()) {
                session.begin();
                wasBegun = false;
            }
            R record = findRecord(session, key);
            if (!wasBegun)
                session.commit();
            return Optional.ofNullable(record);
        });
        return result;
    }

    /**
     * Retrieves from the database the record associated with the specified key.
     */
    protected abstract R findRecord(SSessionJdbc session, K key);

    public synchronized Collection<R> getAllRecords() {
        SSessionJdbc session = SSessionJdbc.getThreadLocalSession();
        if (cachedSessions.containsKey(session)) {
            return getSessionLocalMap(session).values().stream()
                    .filter(Optional::isPresent).map(Optional::get)
                    .collect(Collectors.toList());
        } else {
            boolean wasBegun = true;
            if (!session.hasBegun()) {
                session.begin();
                wasBegun = false;
            }
            List<R> list = findAllRecords(session);
            Map<K, Optional<R>> map = getSessionLocalMap(session);
            for (R record : list) {
                map.put(getKeyFromRecord(record), Optional.of(record));
            }
            cachedSessions.put(session, true);
            if (!wasBegun)
                session.commit();
            return list;
        }
    }

    /**
     * Retrieves all the records from the database.
     */
    protected abstract List<R> findAllRecords(SSessionJdbc session);

    /**
     * Extracts the key from the record.
     */
    protected abstract K getKeyFromRecord(R record);
}