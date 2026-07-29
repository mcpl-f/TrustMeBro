package me.fulcanelly.trust.me.bro.database.repository.model;

import lombok.Value;

@Value
public class SuspiciousPlayerStat {

    String player;
    long interactionsSum;
    int ownersInvolvedCount;
}
