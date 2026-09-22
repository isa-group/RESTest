/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.core.event;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The one place a run announces what it is doing, and the place every listener hears it.
 *
 * <p>Whoever is running the tests hands events to this and carries straight on. Handing one over
 * never waits for anybody: the event is put in a queue and a separate thread walks it out to the
 * listeners. That is deliberate and is the whole reason this class exists. Judging a reply against
 * a schema, writing a line to the screen or appending to a file all take time, and none of that
 * time should be time in which no request is being sent.
 *
 * <p>One thread delivers everything, so listeners are called one at a time, in the order the events
 * happened, and a listener can keep a running total in a plain field without locking. A listener
 * may itself announce something - this is how an oracle turns an attempt into a fault - and what it
 * announces joins the back of the queue.
 *
 * <p>A listener that throws does not stop the run or the other listeners. The failure is counted,
 * and {@link #listenerFailures()} says how many there have been, so a broken report shows up as a
 * number rather than as silence. What the listener was half-way through is its own business: it is
 * not told what happened and nothing here undoes it, so a listener that keeps something of its own
 * has to be able to be interrupted anywhere. Running out of memory is the one failure still left to
 * end this thread, because a listener is not the reason for it and nothing here could carry on
 * anyway.
 *
 * <p>There is one of these per run and nothing static in it, so two runs in the same program never
 * see each other's events.
 */
public final class EventStream implements AutoCloseable {

    /**
     * How long {@link #close()} waits for the listeners to catch up before giving up on them. A run
     * that has finished should not be held open indefinitely by one listener that has stopped
     * making progress; what was still queued is reported as undelivered instead.
     */
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Put on the queue by {@link #close()} to say there is nothing more coming. It is deliberately
     * not a {@link RunEvent}: the list of events is closed, and a marker nobody announces has no
     * business being on it.
     */
    private static final Object END = new Object();

    private final BlockingQueue<Object> queued = new LinkedBlockingQueue<>();

    /**
     * What a listener announced while being told something. Written and read only by the one thread
     * that delivers, so it needs no locking of its own.
     */
    private final Queue<RunEvent> raised = new ArrayDeque<>();
    private final List<RunListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Held while announcing, and held exclusively while closing.
     *
     * <p>Without it, a run ending while another thread is still announcing has a gap between that
     * thread finding the run open and its event reaching the queue. The end marker can pass through
     * the gap, and the event is then queued behind a marker nobody waits on any more: found, and
     * then silently never reported. Announcing takes a shared hold, so any number of threads
     * announce at once and only the ending waits.
     */
    private final ReadWriteLock ending = new ReentrantReadWriteLock();

    private final Thread deliverer;

    public EventStream() {
        this.deliverer = Thread.ofVirtual().name("restest-events").start(this::deliverUntilEnd);
    }

    /**
     * Adds a listener. It hears everything announced from now on, and nothing announced before.
     *
     * @throws IllegalStateException if the run has already finished
     */
    public void subscribe(RunListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed.get()) {
            throw new IllegalStateException(
                    "a run that has already finished has nothing left to listen to");
        }
        listeners.add(listener);
    }

    /**
     * Announces something. Returns immediately, whatever the listeners are busy with.
     *
     * <p>A listener may still announce something after the run has ended, and must be able to: when
     * the run finishes there are usually attempts still queued, and a fault an oracle finds in the
     * last of them would otherwise be found and then thrown away. Everyone else is refused, because
     * announcing something into a run that is over means the announcement goes nowhere.
     *
     * @throws IllegalStateException if the run has finished and this is not a listener speaking
     */
    public void publish(RunEvent event) {
        Objects.requireNonNull(event, "event");
        if (Thread.currentThread() == deliverer) {
            published.incrementAndGet();
            raised.add(event);
            return;
        }
        ending.readLock().lock();
        try {
            if (closed.get()) {
                throw new IllegalStateException("a run that has already finished cannot announce '"
                        + event.getClass().getSimpleName() + "'");
            }
            published.incrementAndGet();
            queued.add(event);
        } finally {
            ending.readLock().unlock();
        }
    }

    /** How many events have been announced. */
    public long published() {
        return published.get();
    }

    /** How many have reached the listeners. Equal to {@link #published()} once closed. */
    public long delivered() {
        return delivered.get();
    }

    /** How many times a listener threw while being told something. */
    public long listenerFailures() {
        return failures.get();
    }

    /** How many events were still waiting when the listeners ran out of time. Normally zero. */
    public long undelivered() {
        return published.get() - delivered.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Ends the run's announcements and waits for the listeners to work through what is left, so
     * that a report is complete by the time this returns.
     *
     * <p>Calling it twice does nothing the second time. Calling it from inside a listener would be
     * asking a thread to wait for itself, so in that case it only stops further announcements.
     */
    @Override
    public void close() {
        ending.writeLock().lock();
        try {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            queued.add(END);
        } finally {
            ending.writeLock().unlock();
        }
        if (Thread.currentThread() == deliverer) {
            return;
        }
        try {
            if (!deliverer.join(DRAIN_TIMEOUT)) {
                deliverer.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Takes events off the queue and hands them to every listener until told there is nothing more.
     *
     * <p>The end marker is put back if anything is still queued behind it. Without that, the last
     * attempts of a run would be delivered and the marker reached before them.
     */
    private void deliverUntilEnd() {
        while (true) {
            Object taken;
            try {
                taken = queued.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (taken == END) {
                if (queued.isEmpty()) {
                    return;
                }
                queued.add(END);
                continue;
            }
            deliver((RunEvent) taken);
        }
    }

    /**
     * Tells every listener about one event, and then about anything a listener announced in reply,
     * before going back for the next one.
     *
     * <p>That ordering is the whole point of the {@link #raised} queue. An oracle hears that an
     * attempt has finished and announces the faults it found in it; those faults belong beside that
     * attempt, not after everything else that had already been queued up behind it - which, at the
     * end of a run, would mean after the summary that was supposed to count them.
     */
    private void deliver(RunEvent first) {
        Deque<RunEvent> pending = new ArrayDeque<>();
        pending.add(first);
        while (!pending.isEmpty()) {
            RunEvent event = pending.poll();
            for (RunListener listener : listeners) {
                try {
                    listener.on(event);
                } catch (RuntimeException | LinkageError | StackOverflowError broken) {
                    // A report that cannot cope with one event must not cost the rest of the run,
                    // nor the other listeners' view of it. Counted so it is visible afterwards.
                    //
                    // LinkageError is caught alongside, which is unusual and was not a guess: a
                    // listener whose own library is missing a class throws one, and without this it
                    // killed this thread outright and took the whole of the rest of the run's
                    // reporting with it, silently. That is precisely the failure this class exists
                    // to prevent, and a broken listener is a broken listener however it says so.
                    //
                    // StackOverflowError for the same reason, found the same way: a listener that
                    // walks something the API sent can be handed something nested deeply enough to
                    // run out of room, and this thread dying takes every report with it while
                    // leaving nothing blamed. Running out of room inside one listener says that
                    // listener met something it could not cope with, not that the machine is in
                    // trouble. Errors that do say that are still left to propagate.
                    failures.incrementAndGet();
                }
            }
            delivered.incrementAndGet();
            RunEvent inReply;
            while ((inReply = raised.poll()) != null) {
                pending.add(inReply);
            }
        }
    }
}
