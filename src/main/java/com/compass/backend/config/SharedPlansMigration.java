package com.compass.backend.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves the data of the old shared plans into shared activities, once, the first time the app starts on a
 * database that still has them. Activities and tasks used to live in a plan ({@code activities.plan_id},
 * {@code tasks.plan_id}); now each activity has its own members, so:
 * <ul>
 * <li>every activity becomes an activity owned by its plan's owner, with the plan's members as its members;</li>
 * <li>every task stays in its activity, assigned to the student who created it;</li>
 * <li>invitation links are not kept (they were for plans), and the old tables are dropped.</li>
 * </ul>
 * Ids are kept, so links to /activity/{id} keep working. New tables are used because SQLite cannot drop the
 * old NOT NULL plan_id columns, and Hibernate creates them before this runs.
 */
@Component
public class SharedPlansMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SharedPlansMigration.class);

    private static final List<String> OLD_TABLES = List.of("tasks", "activities", "plan_invitations", "plan_members",
        "plans");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public SharedPlansMigration(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("plans")) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            int moved = 0;
            if (tableExists("activities") && tableExists("plan_members")) {
                moved = jdbc.update("""
                    INSERT INTO study_activities (id, owner_id, title, subject, due_date, done, done_by, created_by, created_at)
                    SELECT a.id, p.owner_id, a.title, a.subject, a.due_date, a.done, a.done_by, a.created_by, a.created_at
                    FROM activities a JOIN plans p ON p.id = a.plan_id
                    WHERE a.id NOT IN (SELECT id FROM study_activities)""");
                jdbc.update("""
                    INSERT INTO activity_members (activity_id, user_id, joined_at)
                    SELECT a.id, m.user_id, m.joined_at
                    FROM activities a JOIN plan_members m ON m.plan_id = a.plan_id
                    WHERE NOT EXISTS (SELECT 1 FROM activity_members x WHERE x.activity_id = a.id AND x.user_id = m.user_id)
                    ORDER BY a.id, m.id""");
                if (tableExists("tasks")) {
                    jdbc.update("""
                        INSERT INTO activity_tasks (id, activity_id, title, details, due_date, estimated_minutes,
                            assignee_id, done, done_by, created_by, created_at)
                        SELECT t.id, t.activity_id, t.title, t.details, t.due_date, t.estimated_minutes,
                            t.created_by, t.done, t.done_by, t.created_by, t.created_at
                        FROM tasks t JOIN activities a ON a.id = t.activity_id
                        WHERE t.id NOT IN (SELECT id FROM activity_tasks)""");
                }
            }
            for (String table : OLD_TABLES) {
                jdbc.execute("DROP TABLE IF EXISTS " + table);
            }
            log.info("Moved {} activities out of the old shared plans and dropped the plan tables.", moved);
        });
    }

    private boolean tableExists(String name) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?", Integer.class, name);
        return count != null && count > 0;
    }

}
