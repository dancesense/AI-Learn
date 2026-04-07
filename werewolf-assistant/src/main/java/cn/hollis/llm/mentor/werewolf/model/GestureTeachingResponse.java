package cn.hollis.llm.mentor.werewolf.model;

import java.util.List;

public record GestureTeachingResponse(
        String deckName,
        List<GestureCard> cards
) {
}
