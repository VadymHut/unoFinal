package com.example.uno.service;

import com.example.uno.dto.*;
import com.example.uno.exception.GameFullException;
import com.example.uno.exception.IllegalMoveException;
import com.example.uno.exception.NotYourTurnException;
import com.example.uno.mapper.CardMapper;
import com.example.uno.model.*;
import com.example.uno.repository.AppUserRepo;
import com.example.uno.repository.GameHistoryRepo;
import com.example.uno.repository.GamePlayerRepo;
import com.example.uno.repository.UnoGameRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GameService {
    private final SimpMessagingTemplate broker;
    private final UnoGameRepo gameRepo;
    private final AppUserRepo userRepo;
    private final GamePlayerRepo gpRepo;
    private final GameHistoryRepo gameHistoryRepo;
    private final DeckService deckSvc;
    private final TurnService turnSvc;
    private final CardMapper cardMapper;

    private final ObjectMapper json = new ObjectMapper();

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final java.security.SecureRandom RNG = new java.security.SecureRandom();

    @Transactional
    public String createGame(Long hostUserId, boolean ranked) {
        AppUser hostUser = userRepo.findById(hostUserId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + hostUserId));
        UnoGame game = UnoGame.builder()
                .gameCode(randomCode())
                .status(GameStatus.WAITING)
                .currentPlayerPos(0)
                .drawPileJson("[]")
                .discardPileJson("[]")
                .ranked(ranked)
                .build();
        gameRepo.save(game);

        GamePlayer host = GamePlayer.builder()
                .user(hostUser)
                .game(game)
                .seatPos(0)
                .handJson("[]")
                .build();

        game.getPlayers().add(host);
        gpRepo.save(host);

        return game.getGameCode();
    }

    @Transactional
    public GameStateDTO join(String gameCode, Long joinerId) {
        UnoGame game = gameRepo.findByGameCode(gameCode)
                .orElseThrow(() -> new NoSuchElementException("Game not found"));

        if (game.getPlayers().size() >= 2)
            throw new GameFullException("Game is full");

        boolean alreadyInGame = game.getPlayers().stream()
                .anyMatch(p -> p.getUser().getId().equals(joinerId));
        if (alreadyInGame)
            throw new IllegalStateException("Already joined");

        GamePlayer gp = GamePlayer.builder()
                .user(userRepo.getReferenceById(joinerId))
                .game(game)
                .seatPos(1)
                .handJson("[]")
                .build();

        game.getPlayers().add(gp);
        gpRepo.save(gp);

        dealInitialCards(game);
        game.setStatus(GameStatus.IN_PROGRESS);

        int startingSeat = new java.security.SecureRandom().nextBoolean() ? 0 : 1;
        game.setCurrentPlayerPos(startingSeat);

        for (GamePlayer player : game.getPlayers()) {
            Long playerId = player.getUser().getId();
            GameStateDTO state = snapshot(game, playerId);
            System.out.println("📡 Sending game state to userId: " + playerId);
            broker.convertAndSendToUser(playerId.toString(), "/queue/game", state);
        }


        return snapshot(game, joinerId);
    }


    @Transactional
    public GameStateDTO applyMove(Long gameId, Long actorId, MoveDTO move) {
        UnoGame game = gameRepo.findById(gameId)
                .orElseThrow(() -> new NoSuchElementException("Game not found"));

        GamePlayer gp = gpRepo.findByGameIdAndUserId(gameId, actorId)
                .orElseThrow(() -> new IllegalStateException("Not in game"));

        if (!Objects.equals(game.getCurrentPlayerPos(), gp.getSeatPos()))
            throw new NotYourTurnException("Not your turn");

        switch (move.type()) {
            case DRAW_CARD -> handleDraw(game, gp);
            case PLAY_CARD -> handlePlay(game, gp, move.card(), move.chosenColor());
            case CALL_UNO -> gp.setSaidUno(true);
        }

        for (GamePlayer player : game.getPlayers()) {
            GameStateDTO state = snapshot(game, player.getUser().getId());
            broker.convertAndSend("/user/" + player.getUser().getId() + "/queue/game", state);
        }

        return snapshot(game, actorId);
    }

    private void dealInitialCards(UnoGame game) {
        Deque<Card> deck = deckSvc.shuffledDeck();

        for (int i = 0; i < 7; i++) {
            for (GamePlayer gp : game.getPlayers()) {
                giveCard(gp, deck.pop());
            }
        }

        Card first;
        do first = deck.pop();
        while (first.getColor() == Color.WILD);

        game.setDiscardPileJson(writeJson(List.of(first)));
        game.setDrawPileJson(writeJson(deck));
        game.setActiveColor(first.getColor());
        game.setActiveRank(first.getRank());
    }

    private void handleDraw(UnoGame game, GamePlayer gp) {
        Deque<Card> draw = readDeck(game.getDrawPileJson());
        if (draw.isEmpty()) reshuffle(game, draw);

        if (!draw.isEmpty()) giveCard(gp, draw.pop());

        game.setDrawPileJson(writeJson(draw));
        turnSvc.advanceTurn(game, null);
    }

    private void handlePlay(UnoGame game, GamePlayer gp, CardDTO dto, Color chosenColor) {
        Card played = cardMapper.toEntity(dto);
        List<Card> hand = readHand(gp.getHandJson());

        if (!hand.contains(played)) {
            throw new IllegalMoveException("You don't have that card.");
        }

        if (!isPlayable(game, played)) {
            throw new IllegalMoveException("Card not playable on current color/rank.");
        }

        hand.remove(played);
        gp.setHandJson(writeJson(hand));
        gpRepo.save(gp);

        List<Card> discard = readJson(game.getDiscardPileJson());
        discard.add(played);
        game.setDiscardPileJson(writeJson(discard));

        if (played.getColor() == Color.WILD) {
            if (chosenColor == null || chosenColor == Color.WILD) {
                throw new IllegalMoveException("You must choose a color when playing a WILD card.");
            }
            game.setActiveColor(chosenColor);
        } else {
            game.setActiveColor(played.getColor());
        }

        game.setActiveRank(played.getRank());

        if (played.getRank() == Rank.DRAW_TWO || played.getRank() == Rank.WILD_DRAW_FOUR) {
            int opponentSeat = gp.getSeatPos() ^ 1;
            GamePlayer opponent = game.getPlayers().stream()
                    .filter(p -> p.getSeatPos() == opponentSeat)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Opponent not found."));
            int drawCount = (played.getRank() == Rank.DRAW_TWO) ? 2 : 4;
            givePenalty(opponent, drawCount);
        }

        if (hand.size() == 1 && !gp.isSaidUno()) {
            givePenalty(gp, 2);
        }

        gp.setSaidUno(false);
        gpRepo.save(gp);

        if (hand.isEmpty()) {
            game.setStatus(GameStatus.FINISHED);


            Long winnerId = gp.getUser().getId();

            if (game.isRanked()) {
                updateEloAfterGame(game, winnerId);
            }

            saveGameHistory(game, winnerId);
        }

        turnSvc.advanceTurn(game, played.getRank());

        gameRepo.save(game);
    }


    private void givePenalty(GamePlayer gp, int n) {
        UnoGame game = gp.getGame();
        Deque<Card> draw = readDeck(game.getDrawPileJson());
        if (draw.isEmpty()) reshuffle(game, draw);

        for (int i = 0; i < n && !draw.isEmpty(); i++) {
            giveCard(gp, draw.pop());
        }

        game.setDrawPileJson(writeJson(draw));
    }

    private void reshuffle(UnoGame game, Deque<Card> draw) {
        Deque<Card> discard = readDeck(game.getDiscardPileJson());
        if (discard.size() <= 1) return;

        Card top = discard.removeLast();
        List<Card> toShuffle = new ArrayList<>(discard);
        Collections.shuffle(toShuffle);
        draw.addAll(toShuffle);

        discard.clear();
        discard.add(top);

        game.setDrawPileJson(writeJson(draw));
        game.setDiscardPileJson(writeJson(discard));
    }

    public GameStateDTO snapshot(UnoGame game, Long viewerId) {
        System.out.println("snapshot() for userId: " + viewerId);

        GamePlayer you = game.getPlayers().stream()
                .filter(p -> p.getUser() != null && p.getUser().getId().equals(viewerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("You are not part of this game"));

        GamePlayer opp = game.getPlayers().stream()
                .filter(p -> p.getUser() != null && !p.getUser().getId().equals(viewerId))
                .findFirst()
                .orElse(null);

        List<CardDTO> yourHand = readHand(you.getHandJson())
                .stream()
                .map(cardMapper::toDto)
                .toList();

        int yourHandSize = yourHand.size();

        int oppSize = (opp == null) ? 0 : readHand(opp.getHandJson()).size();
        Deque<Card> discard = readDeck(game.getDiscardPileJson());
        Card top = discard.isEmpty() ? null : discard.getLast();

        CardDTO topCard = (top != null) ? cardMapper.toDto(top) : null;

        System.out.println("  -> topCard: " + topCard);
        System.out.println("  -> activeColor: " + game.getActiveColor());
        System.out.println("  -> opponentHandSize: " + oppSize);
        System.out.println("  -> yourHand size: " + yourHandSize);

        String yourUsername = you.getUser().getUsername();
        String opponentUsername = (opp != null && opp.getUser() != null)
                ? opp.getUser().getUsername() : "Waiting...";

        return GameStateDTO.builder()
                .gameId(game.getId())
                .status(game.getStatus())
                .yourSeat(you.getSeatPos())
                .currentSeat(game.getCurrentPlayerPos())
                .yourHand(yourHand)
                .opponentHandSize(oppSize)
                .topCard(topCard)
                .activeColor(game.getActiveColor())
                .activeRank(game.getActiveRank())
                .ranked(game.isRanked())
                .yourElo(you.getUser().getElo())
                .opponentElo(opp != null ? opp.getUser().getElo() : 0)
                .yourUsername(yourUsername)
                .opponentUsername(opponentUsername)
                .build();
    }


    private List<Card> readHand(String jsonTxt) {
        try {
            return json.readValue(jsonTxt, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Deque<Card> readDeck(String jsonTxt) {
        return new ArrayDeque<>(readHand(jsonTxt));
    }

    private String writeJson(Object obj) {
        try {
            return json.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Card> readJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return new ArrayList<>();
        try {
            return json.readValue(jsonStr, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }


    private void giveCard(GamePlayer gp, Card card) {
        List<Card> hand = new ArrayList<>(readHand(gp.getHandJson()));
        hand.add(card);
        gp.setHandJson(writeJson(hand));
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUM.charAt(RNG.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    private boolean isPlayable(UnoGame game, Card played) {
        Color activeColor = game.getActiveColor();
        Rank activeRank = game.getActiveRank();

        if (played.getColor() == Color.WILD) {
            return true;
        }
        return played.getColor() == activeColor || played.getRank() == activeRank;
    }

    private void updateEloAfterGame(UnoGame game, Long winnerId) {
        List<GamePlayer> players = game.getPlayers();
        if (players.size() != 2) return;

        AppUser winner = players.stream()
                .filter(p -> p.getUser().getId().equals(winnerId))
                .map(GamePlayer::getUser)
                .findFirst().orElseThrow();

        AppUser loser = players.stream()
                .filter(p -> !p.getUser().getId().equals(winnerId))
                .map(GamePlayer::getUser)
                .findFirst().orElseThrow();

        int winnerElo = winner.getElo();
        int loserElo = loser.getElo();

        int[] newElos = calculateElo(winnerElo, loserElo, true);
        winner.setElo(newElos[0]);
        loser.setElo(newElos[1]);

        userRepo.save(winner);
        userRepo.save(loser);
    }

    private int[] calculateElo(int winnerElo, int loserElo, boolean winnerWon) {
        int k = 32;
        double expectedWin = 1 / (1 + Math.pow(10, (loserElo - winnerElo) / 400.0));

        int newWinnerElo = (int) Math.round(winnerElo + k * (1 - expectedWin));
        int newLoserElo = (int) Math.round(loserElo + k * (0 - (1 - expectedWin)));

        return new int[]{newWinnerElo, newLoserElo};
    }

    private void saveGameHistory(UnoGame game, Long winnerId) {
        for (GamePlayer gp : game.getPlayers()) {
            AppUser user = gp.getUser();
            boolean isWinner = Objects.equals(user.getId(), winnerId);

            GameHistory entry = GameHistory.builder()
                    .player(user)
                    .gameCode(game.getGameCode())
                    .ranked(game.isRanked())
                    .winner(isWinner)
                    .finishedAt(Instant.now())
                    .finalElo(user.getElo())
                    .build();

            gameHistoryRepo.save(entry);
        }
    }


}