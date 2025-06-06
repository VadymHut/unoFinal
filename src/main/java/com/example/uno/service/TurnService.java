package com.example.uno.service;

import com.example.uno.model.UnoGame;
import com.example.uno.model.Rank;
import org.springframework.stereotype.Service;

@Service
public class TurnService {

    public void advanceTurn(UnoGame game, Rank playedRank) {
        int current = game.getCurrentPlayerPos();

        if (playedRank == Rank.SKIP || playedRank == Rank.REVERSE ||
                playedRank == Rank.DRAW_TWO || playedRank == Rank.WILD_DRAW_FOUR) {
            game.setCurrentPlayerPos(current);
        } else {
            game.setCurrentPlayerPos(current ^ 1);
        }
    }

}
