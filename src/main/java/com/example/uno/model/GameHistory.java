package com.example.uno.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GameHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String gameCode;
    private boolean ranked;
    private Instant finishedAt;

    @ManyToOne
    @JoinColumn(name = "username")
    private AppUser player;

    private boolean winner;

    private int finalElo;
}

