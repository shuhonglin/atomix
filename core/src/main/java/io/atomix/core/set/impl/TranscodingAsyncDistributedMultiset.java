/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core.set.impl;

import com.google.common.collect.Maps;
import io.atomix.core.collection.impl.TranscodingAsyncDistributedCollection;
import io.atomix.core.set.AsyncDistributedMultiset;
import io.atomix.core.set.DistributedMultiset;
import io.atomix.core.set.SetEvent;
import io.atomix.core.set.SetEventListener;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Transcoding multiset.
 */
public class TranscodingAsyncDistributedMultiset<E1, E2> extends TranscodingAsyncDistributedCollection<E1, E2> implements AsyncDistributedMultiset<E1> {
  private final AsyncDistributedMultiset<E2> backingMultiset;
  private final Function<E2, E1> entryDecoder;
  private final Map<SetEventListener<E1>, InternalBackingSetEventListener> listeners = Maps.newIdentityHashMap();

  public TranscodingAsyncDistributedMultiset(
      AsyncDistributedMultiset<E2> backingMultiset,
      Function<E1, E2> entryEncoder,
      Function<E2, E1> entryDecoder) {
    super(backingMultiset, entryEncoder, entryDecoder);
    this.backingMultiset = backingMultiset;
    this.entryDecoder = entryDecoder;
  }

  @Override
  public CompletableFuture<Void> addListener(SetEventListener<E1> listener) {
    synchronized (listeners) {
      InternalBackingSetEventListener backingSetListener =
          listeners.computeIfAbsent(listener, k -> new InternalBackingSetEventListener(listener));
      return backingMultiset.addListener(backingSetListener);
    }
  }

  @Override
  public CompletableFuture<Void> removeListener(SetEventListener<E1> listener) {
    synchronized (listeners) {
      InternalBackingSetEventListener backingMapListener = listeners.remove(listener);
      if (backingMapListener != null) {
        return backingMultiset.removeListener(backingMapListener);
      } else {
        return CompletableFuture.completedFuture(null);
      }
    }
  }

  @Override
  public DistributedMultiset<E1> sync(Duration operationTimeout) {
    return new BlockingDistributedMultiset<>(this, operationTimeout.toMillis());
  }

  private class InternalBackingSetEventListener implements SetEventListener<E2> {

    private final SetEventListener<E1> listener;

    InternalBackingSetEventListener(SetEventListener<E1> listener) {
      this.listener = listener;
    }

    @Override
    public void event(SetEvent<E2> event) {
      listener.event(new SetEvent<>(
          event.name(),
          event.type(),
          entryDecoder.apply(event.entry())));
    }
  }
}