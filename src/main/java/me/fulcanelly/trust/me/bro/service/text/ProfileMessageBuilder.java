package me.fulcanelly.trust.me.bro.service.text;

import lombok.RequiredArgsConstructor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.SQLException;

import me.fulcanelly.trust.me.bro.bootstrap.bundles.LocalRepositories;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService;

/**
 * Builds the /myprofile Telegram message and its buttons.
 *
 * Shared by the command (first message) and the callback handler (edits when a
 * button
 * is pressed), so both always render the same thing.
 *
 * ---
 * MAIN: profile text [My reputation] [My ratings]
 * REPUTATION: profile text + "Your reputation:" [Back]
 * RATINGS: profile text + "Your ratings:" [Back]
 * ---
 */
@RequiredArgsConstructor
public final class ProfileMessageBuilder {

    public enum Section {
        MAIN,
        REPUTATION,
        RATINGS
    }

    private final LocalizationService messages;
    private final TrustCallbackPayloadService callbackPayloads;
    private final LocalRepositories repositories;

    public String buildText(String player, Section section) throws SQLException {
        var trust = repositories.getTrust();
        var reports = repositories.getReports();

        String profile = messages.format(
                "telegram.command.myprofile.text",
                "trusters", trust.countTrusters(player),
                "reporters", reports.countReporters(player),
                "trusted", trust.countTrusted(player),
                "watched", 0, // TODO: watch relation is not implemented yet
                "reported", reports.countReportedByOwner(player),
                "pending", repositories.getInteractionCounts().countUndecidedInteractors(player));

        // TODO: sections are stubs for now, they only add a header line
        switch (section) {
            case REPUTATION:
                return profile + "\n\n" + messages.format("telegram.command.myprofile.reputation-section");
            case RATINGS:
                return profile + "\n\n" + messages.format("telegram.command.myprofile.ratings-section");
            default:
                return profile;
        }
    }

    /**
     * @param allowedTelegramUserId the only Telegram user whose presses are
     *                              accepted
     */
    @SuppressWarnings("unchecked")
    public String buildKeyboard(Section section, long allowedTelegramUserId) {
        JSONArray totalRows = new JSONArray();
        JSONArray tempTow;

        if (section == Section.MAIN) {
            // First row
            JSONArray tempRow = new JSONArray();
            tempRow.add(button(
                    messages.format("telegram.button.my-reputation"),
                    callbackPayloads.encodeProfile(
                            TrustCallbackPayloadService.ACTION_PROFILE_REPUTATION, allowedTelegramUserId)));
            totalRows.add(tempRow);
            // Second row
            tempRow = new JSONArray();
            tempRow.add(button(
                    messages.format("telegram.button.my-ratings"),
                    callbackPayloads.encodeProfile(
                            TrustCallbackPayloadService.ACTION_PROFILE_RATINGS, allowedTelegramUserId)));
            totalRows.add(tempRow);

        } else {
            tempTow = new JSONArray();
            tempTow.add(button(
                    messages.format("telegram.button.back"),
                    callbackPayloads.encodeProfile(
                            TrustCallbackPayloadService.ACTION_PROFILE_BACK, allowedTelegramUserId)));
            totalRows.add(tempTow);
        }


        JSONObject keyboard = new JSONObject();
        keyboard.put("inline_keyboard", totalRows);
        return keyboard.toJSONString();
    }

    @SuppressWarnings("unchecked")
    private JSONObject button(String text, String callbackData) {
        JSONObject button = new JSONObject();
        button.put("text", text);
        button.put("callback_data", callbackData);
        return button;
    }
}
