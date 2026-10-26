package me.fulcanelly.trust.me.bro.service.text;

import static me.fulcanelly.trust.me.bro.service.util.TelegramMarkdown.escape;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;

/**
 * Strings and JSON of the /rep message: the text templates, Markdown escaping and
 * the inline keyboard.
 *
 * It knows nothing about sections or the database. {@code ProfileCallbackHandler}
 * decides what to show and passes the numbers, titles and buttons in.
 */
@RequiredArgsConstructor
public final class ProfileMessageBuilder {

    /** One inline keyboard button. */
    @Value
    public static class Button {

        String text;
        String callbackData;
    }

    private final LocalizationService messages;

    /**
     * @param path titles from the root's child down to the shown section, empty for
     *             the root; added under the profile as "Path / To / Section:"
     */
    public String profileText(
            String player,
            int trusters,
            int reporters,
            int trusted,
            int watched,
            int reported,
            int pending,
            List<String> path) {
        String profile = messages.format(
                "telegram.command.myprofile.text",
                "player", escape(player),
                "trusters", trusters,
                "reporters", reporters,
                "trusted", trusted,
                "watched", watched,
                "reported", reported,
                "pending", pending);

        if (path.isEmpty()) {
            return profile;
        }
        return profile + "\n\n" + pathLine(path);
    }

    /**
     * The screen of one player picked from a list.
     *
     * @param path           as in {@link #profileText}, the listed player is added as its last step
     * @param towardsPlayer  interactions of the listed player towards {@code player}
     * @param towardsOther   interactions of {@code player} towards the listed player
     */
    public String playerText(
            String player,
            String listedPlayer,
            List<String> path,
            int towardsPlayer,
            int towardsOther) {
        List<String> steps = new ArrayList<>(path);
        steps.add(messages.format("telegram.command.myprofile.player-button", "player", escape(listedPlayer)));

        return messages.format(
                "telegram.command.myprofile.player-view",
                "player", escape(player),
                "other", escape(listedPlayer),
                "path", pathLine(steps),
                "towardsPlayer", towardsPlayer,
                "towardsOther", towardsOther);
    }

    /** @param rows buttons of each keyboard row, top to bottom */
    @SuppressWarnings("unchecked")
    public String keyboard(List<List<Button>> rows) {
        JSONArray jsonRows = new JSONArray();
        for (List<Button> row : rows) {
            JSONArray jsonRow = new JSONArray();
            for (Button button : row) {
                JSONObject json = new JSONObject();
                json.put("text", button.getText());
                json.put("callback_data", button.getCallbackData());
                jsonRow.add(json);
            }
            jsonRows.add(jsonRow);
        }

        JSONObject keyboard = new JSONObject();
        keyboard.put("inline_keyboard", jsonRows);
        return keyboard.toJSONString();
    }

    /** "⭐ Reputation / ✅ Trusted by:" */
    private String pathLine(List<String> steps) {
        return String.join(" / ", steps) + ":";
    }
}
