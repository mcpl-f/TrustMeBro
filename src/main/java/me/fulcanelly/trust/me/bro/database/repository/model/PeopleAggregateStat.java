package me.fulcanelly.trust.me.bro.database.repository.model;

import lombok.Value;

/**
 * A player ranked by how many other players trust/report them.
 */
@Value
public class PeopleAggregateStat {

    String player;
    int peopleCount;
}
