package com.example.uno.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    @ManyToOne
    @JoinColumn(nullable = false)
    private UnoGame game;

    @ManyToOne
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    private ActionType type;

    @Embedded
    private Card playedCard;

    private Instant occurredAt;


}