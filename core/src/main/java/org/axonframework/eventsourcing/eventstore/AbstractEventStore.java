/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Abstract implementation of an {@link EventStore} that uses a {@link EventStorageEngine} to store and load events.
 *
 * @author Rene de Waele
 */
public abstract class AbstractEventStore extends AbstractEventBus implements EventStore {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddedEventStore.class);

    private final EventStorageEngine storageEngine;

    /**
     * Initializes an event store with given {@code storageEngine} and {@link NoOpMessageMonitor}.
     *
     * @param storageEngine The storage engine used to store and load events
     */
    protected AbstractEventStore(EventStorageEngine storageEngine) {
        this(storageEngine, NoOpMessageMonitor.INSTANCE);
    }

    /**
     * Initializes an event store with given {@code storageEngine} and {@code messageMonitor}.
     *
     * @param storageEngine  The storage engine used to store and load events
     * @param messageMonitor The monitor used to record event publications
     */
    protected AbstractEventStore(EventStorageEngine storageEngine,
                                 MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(messageMonitor);
        this.storageEngine = storageEngine;
    }

    @Override
    protected void prepareCommit(List<? extends EventMessage<?>> events) {
        storageEngine.appendEvents(events);
        super.prepareCommit(events);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns a {@link DomainEventStream} starting with the last stored snapshot of the aggregate
     * followed by subsequent domain events.
     */
    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        Optional<DomainEventMessage<?>> optionalSnapshot;
        try {
            optionalSnapshot = storageEngine.readSnapshot(aggregateIdentifier);
        } catch (Exception | LinkageError e) {
            logger.warn("Error reading snapshot. Reconstructing aggregate from entire event stream. Caused by: {} {}",
                        e.getClass().getName(), e.getMessage());
            optionalSnapshot = Optional.empty();
        }
        if (optionalSnapshot.isPresent()) {
            DomainEventMessage<?> snapshot = optionalSnapshot.get();
            return DomainEventStream.concat(DomainEventStream.of(snapshot),
                                            storageEngine.readEvents(aggregateIdentifier,
                                                                     snapshot.getSequenceNumber() + 1));
        } else {
            return storageEngine.readEvents(aggregateIdentifier);
        }
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        storageEngine.storeSnapshot(snapshot);
    }

    /**
     * Returns the {@link EventStorageEngine} used by the event store.
     *
     * @return The event storage engine used by this event store
     */
    protected EventStorageEngine storageEngine() {
        return storageEngine;
    }
}
