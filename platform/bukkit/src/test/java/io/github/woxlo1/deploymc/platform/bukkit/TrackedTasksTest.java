package io.github.woxlo1.deploymc.platform.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackedTasksTest {

  @Test
  void completesAreForgottenAndRemainingTasksAreCancelled() {
    final List<String> cancelled = new ArrayList<String>();
    TrackedTasks<String> tasks = new TrackedTasks<String>(cancelled::add);

    assertTrue(tasks.register("first"));
    assertTrue(tasks.register("second"));
    tasks.complete("first");
    assertEquals(1, tasks.size());

    assertEquals(Arrays.asList("second"), tasks.cancelAll());
    assertEquals(Arrays.asList("second"), cancelled);
    assertEquals(0, tasks.size());
  }

  @Test
  void cancellationIsIdempotentAndRejectsNewTasks() {
    final List<String> cancelled = new ArrayList<String>();
    TrackedTasks<String> tasks = new TrackedTasks<String>(cancelled::add);
    tasks.register("task");

    tasks.cancelAll();
    tasks.cancelAll();

    assertEquals(Arrays.asList("task"), cancelled);
    assertTrue(tasks.isClosed());
    assertFalse(tasks.register("late"));
    assertEquals(0, tasks.size());
  }
}
