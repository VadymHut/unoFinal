package com.example.uno.service;

import com.example.uno.model.Card;
import com.example.uno.model.Color;
import com.example.uno.model.Rank;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DeckService
{
    public Deque<Card> shuffledDeck()
    {
        List<Card> deck = new ArrayList<>(108);

        for (Color c : List.of(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE))
        {
            deck.add(new Card(c, Rank.ZERO));
            for (Rank r : List.of(Rank.ONE, Rank.TWO, Rank.THREE, Rank.FOUR,
                    Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE,
                    Rank.SKIP, Rank.REVERSE, Rank.DRAW_TWO)) {
                deck.add(new Card(c, r));
                deck.add(new Card(c, r));
            }
        }

        deck.addAll(Collections.nCopies(4, new Card(Color.WILD, Rank.WILD)));
        deck.addAll(Collections.nCopies(4, new Card(Color.WILD, Rank.WILD_DRAW_FOUR)));

        Collections.shuffle(deck);
        return new ArrayDeque<>(deck);
    }
}
