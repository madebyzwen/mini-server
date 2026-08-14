package io.github.madebyzwen.miniserver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Persistence operations consumed by the central HTTP API.
 */
abstract class PersistenceStore {

    abstract JsonElement read(ResolvedPersistenceTarget target, String section)
            throws PersistenceException, SectionNotFoundException;

    abstract JsonObject readAll(ResolvedPersistenceTarget target) throws PersistenceException;

    abstract void write(ResolvedPersistenceTarget target, JsonObject sections)
            throws PersistenceException;

    abstract void remove(ResolvedPersistenceTarget target, String section)
            throws PersistenceException, SectionNotFoundException;

    abstract void clear(ResolvedPersistenceTarget target) throws PersistenceException;
}
