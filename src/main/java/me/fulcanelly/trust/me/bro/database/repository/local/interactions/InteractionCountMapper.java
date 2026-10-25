package me.fulcanelly.trust.me.bro.database.repository.local.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import me.fulcanelly.trust.me.bro.database.repository.model.InteractionCount;

final class InteractionCountMapper implements RowMapper<InteractionCount> {

    @Override
    public InteractionCount map(ResultSet row, StatementContext context) throws SQLException {
        return new InteractionCount(
                row.getLong("id"),
                row.getString("interactor_player"),
                row.getString("owner"),
                row.getInt("count_break_blocks"),
                row.getInt("count_placed_blocks"),
                row.getInt("count_interact_containers"),
                nullableInt(row, "wid"),
                nullableInt(row, "region_corner_a_x"),
                nullableInt(row, "region_corner_a_z"),
                nullableInt(row, "region_corner_b_x"),
                nullableInt(row, "region_corner_b_z"));
    }

    private Integer nullableInt(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }
}
