package me.fulcanelly.trust.me.bro.database.repository.model;

import lombok.Value;

@Value
public class InteractionCount {

    String interactorPlayer;
    String ownerPlayer;
    
    int countBreakBlocks;
    int countPlacedBlocks;
    int countInteractContainers;
}
