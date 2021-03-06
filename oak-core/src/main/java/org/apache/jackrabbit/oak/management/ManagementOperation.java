/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.management;

import static com.google.common.base.Objects.toStringHelper;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static javax.management.openmbean.SimpleType.INTEGER;
import static javax.management.openmbean.SimpleType.STRING;
import static org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean.StatusCode;
import static org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean.StatusCode.FAILED;
import static org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean.StatusCode.INITIATED;
import static org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean.StatusCode.NONE;
import static org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean.StatusCode.RUNNING;
import static org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean.StatusCode.SUCCEEDED;
import static org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean.StatusCode.UNAVAILABLE;
import static org.apache.jackrabbit.oak.management.ManagementOperation.Status.failed;
import static org.apache.jackrabbit.oak.management.ManagementOperation.Status.none;
import static org.apache.jackrabbit.oak.management.ManagementOperation.Status.running;
import static org.apache.jackrabbit.oak.management.ManagementOperation.Status.succeeded;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@code ManagementOperation} is a background, which that can be
 * executed by an {@code Executor}. Its {@link Status} indicates
 * whether execution has already been started, is currently under the
 * way or has already finished.
 *
 * @see org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean
 */
public class ManagementOperation extends FutureTask<Long> {
    private static final Logger LOG = LoggerFactory.getLogger(ManagementOperation.class);
    private static final AtomicInteger idGen = new AtomicInteger();

    protected final int id;
    protected final String name;

    /**
     * Create a new {@code ManagementOperation} of the given name. The name
     * is an informal value attached to this instance.
     *
     * @param name  informal name
     * @param task  task to execute for this operation
     */
    public ManagementOperation(String name, Callable<Long> task) {
        super(task);
        this.id = idGen.incrementAndGet();
        this.name = name;
    }

    private static final Callable<Long> NOP = new Callable<Long>() {
        @Override
        public Long call() throws Exception {
            return 0L;
        }
    };

    /**
     * Each instance of a {@code ManagementOperation} has an unique id
     * associated with it. This id is returned as a part of its
     * {@link #getStatus() status}
     *
     * @return  id of this operation
     */
    public int getId() {
        return id;
    }

    /**
     * Informal name
     * @return  name of this operation
     */
    public String getName() {
        return name;
    }

    /**
     * An operation that is already done with the given {@code value}.
     *
     * @param name  name of the operation
     * @param value value returned by the operation
     * @return  a {@code ManagementOperation} instance that is already done.
     */
    public static ManagementOperation done(String name, final long value) {
        return new ManagementOperation("not started", NOP) {
            @Override
            public boolean isDone() {
                return true;
            }

            @Override
            public Long get() {
                return value;
            }

            @Override
            public void run() {
                throw new IllegalStateException("This task is done");
            }

            @Override
            public Status getStatus() {
                return none(id, name + " not started");
            }
        };
    }

    /**
     * The {@link ManagementOperation.Status status} of this operation:
     * <ul>
     *     <li>{@link Status#running(String) running} if the operation is currently
     *     being executed.</li>
     *     <li>{@link Status#succeeded(String) succeeded} if the operation has terminated
     *     without errors.</li>
     *     <li>{@link Status#failed(String) failed} if the operation has been cancelled,
     *     its thread has been interrupted during execution or the operation has failed
     *     with an exception.</li>
     * </ul>
     *
     * @return  the current status of this operation
     */
    public Status getStatus() {
        if (isCancelled()) {
            return failed(id, name + " cancelled");
        } else if (isDone()) {
            try {
                return succeeded(id, name + " completed in " + formatTime(get()));
            } catch (InterruptedException e) {
                currentThread().interrupt();
                return failed(id, name + " status unknown: " + e.getMessage());
            } catch (ExecutionException e) {
                LOG.error(name + " failed", e.getCause());
                return failed(id, name + " failed: " + e.getCause().getMessage());
            }
        } else {
            return running(id, name + " running");
        }
    }

    private static String formatTime(long nanos) {
        return MINUTES.convert(nanos, NANOSECONDS) + " minutes";
    }

    @Override
    public String toString() {
        return toStringHelper(this)
            .add("name", name)
            .add("id", id)
            .toString();
    }

    /**
     * Status of a {@link ManagementOperation}. One of
     * {@link #unavailable(String)}, {@link #none(String)}, {@link #initiated(String)},
     * {@link #running(String)}, {@link #succeeded(String)} and {@link #failed(String)},
     * the semantics of which correspond to the respective status codes in
     * {@link org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean}.
     */
    public static final class Status {
        public static final String ITEM_CODE = "code";
        public static final String ITEM_ID = "id";
        public static final String ITEM_MESSAGE = "message";
        public static final String[] ITEM_NAMES = new String[] {
                ITEM_CODE, ITEM_ID, ITEM_MESSAGE};

        public static final CompositeType ITEM_TYPES = createItemTypes();

        private static CompositeType createItemTypes() {
            try {
                return new CompositeType("status", "status", ITEM_NAMES, ITEM_NAMES,
                        new OpenType[] {INTEGER, INTEGER, STRING});
            } catch (OpenDataException e) {
                // should never happen
                throw new IllegalStateException(e);
            }
        }

        private final StatusCode code;
        private final int id;
        private final String message;

        private Status(StatusCode code, int id, String message) {
            this.code = code;
            this.id = id;
            this.message = message == null ? "" : message;
        }

        public static Status unavailable(String message) {
            return unavailable(idGen.incrementAndGet(), message);
        }

        public static Status none(String message) {
            return none(idGen.incrementAndGet(), message);
        }

        public static Status initiated(String message) {
            return initiated(idGen.incrementAndGet(), message);
        }

        public static Status running(String message) {
            return running(idGen.incrementAndGet(), message);
        }

        public static Status succeeded(String message) {
            return succeeded(idGen.incrementAndGet(), message);
        }

        public static Status failed(String message) {
            return failed(idGen.incrementAndGet(), message);
        }

        static Status unavailable(int id, String message) {
            return new Status(UNAVAILABLE, id , message);
        }

        static Status none(int id, String message) {
            return new Status(NONE, id, message);
        }

        static Status initiated(int id, String message) {
            return new Status(INITIATED, id, message);
        }

        static Status running(int id, String message) {
            return new Status(RUNNING, id, message);
        }

        static Status succeeded(int id, String message) {
            return new Status(SUCCEEDED, id, message);
        }

        static Status failed(int id, String message) {
            return new Status(FAILED, id, message);
        }

        /**
         * Utility method for converting a {@code CompositeData} encoding
         * of a status to a {@code Status} instance.
         *
         * @param status  {@code CompositeData} encoding of a status
         * @return {@code Status} for {@code status}
         * @throws IllegalArgumentException  if {@code status} is not a valid
         *         encoding of a {@code Status}.
         */
        public static Status fromCompositeData(CompositeData status) {
            int code = toInt(status.get(ITEM_CODE));
            int id = toInt(status.get(ITEM_ID));
            String message = toString(status.get(ITEM_MESSAGE));
            return new Status(StatusCode.values()[code], id, message);
        }

        private static int toInt(Object value) {
            if (value instanceof Integer) {
                return (Integer) value;
            } else {
                throw new IllegalArgumentException("Not an integer value:" + value);
            }
        }

        private static String toString(Object value) {
            if (value instanceof String) {
                return (String) value;
            } else {
                throw new IllegalArgumentException("Not a string value:" + value);
            }
        }

        /**
         * Utility method for converting this instance to a {@code CompositeData}
         * encoding of the respective status.
         *
         * @return {@code CompositeData} of this {@code Status}
         */
        public CompositeData toCompositeData() {
            try {
                Object[] values = new Object[] {code.ordinal(), id, message};
                return new CompositeDataSupport(ITEM_TYPES, ITEM_NAMES, values);
            } catch (OpenDataException e) {
                // should never happen
                throw new IllegalStateException(e);
            }
        }

        public StatusCode getCode() {
            return code;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return code.name;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return SUCCEEDED == code;
        }

        public boolean isFailure() {
            return FAILED == code;
        }

        @Override
        public String toString() {
            return toStringHelper(this)
                .addValue(code)
                .add("id", id)
                .add("message", message)
                .toString();
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }

            Status status = (Status) that;
            return id == status.id && code == status.code && message.equals(status.message);

        }

        @Override
        public int hashCode() {
            int result = code.hashCode();
            result = 31 * result + id;
            result = 31 * result + message.hashCode();
            return result;
        }
    }
}
