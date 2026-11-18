package io.github.woxlo1.deploymc.platform.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** Tracks platform task handles so disable can cancel every task the adapter created. */
public final class TrackedTasks<T> {

  private final Consumer<T> cancelAction;
  private final CopyOnWriteArraySet<T> tasks = new CopyOnWriteArraySet<T>();
  private volatile boolean closed;

  public TrackedTasks(Consumer<T> cancelAction) {
    this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
  }

  /** Registers a task unless shutdown has already started. */
  public boolean register(T task) {
    Objects.requireNonNull(task, "task");
    if (closed) {
      return false;
    }
    return tasks.add(task);
  }

  /** Forgets a task that finished normally. */
  public void complete(T task) {
    tasks.remove(Objects.requireNonNull(task, "task"));
  }

  /** Cancels all remaining tasks exactly once and rejects new registrations. */
  public List<T> cancelAll() {
    closed = true;
    List<T> cancelled = new ArrayList<T>(tasks);
    for (T task : cancelled) {
      cancelAction.accept(task);
    }
    tasks.clear();
    return cancelled;
  }

  /** Returns the number of tasks still tracked. */
  public int size() {
    return tasks.size();
  }

  public boolean isClosed() {
    return closed;
  }
}
