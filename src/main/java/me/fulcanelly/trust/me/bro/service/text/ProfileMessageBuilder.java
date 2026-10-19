package me.fulcanelly.trust.me.bro.service.text;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.fulcanelly.trust.me.bro.bootstrap.bundles.LocalRepositories;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;
import me.fulcanelly.trust.me.bro.service.util.TrustCallbackPayloadService;

/**
 * Builds the /myprofile Telegram message and its buttons.
 *
 * Shared by the command (first message) and the callback handler (edits when a
 * button is pressed), so both always render the same thing.
 *
 * Every screen is the profile text plus "Path / To / Section:". A menu section
 * has its children as buttons, a list section has its players as buttons, five
 * per page with a [<] 2 / 5 [>] row. Back leads to the parent:
 *
 * ---
 * MAIN
 * |- REPUTATION: TRUSTERS, REPORTERS
 * `- RATINGS: TRUSTED, WATCHED, REPORTED, PENDING
 * ---
 */
@RequiredArgsConstructor
public final class ProfileMessageBuilder {

    public static final int FIRST_PAGE = 0;

    private static final int PAGE_SIZE = 5;

    @Getter
    @RequiredArgsConstructor
    public enum Section { // TODO isnt this duplicated with the enum in TrustCallbackPayloadService?
        // //maybe merge them or remove one of them
        MAIN(null, "p", null),
        REPUTATION(MAIN, "pr", "reputation"),
        TRUSTERS(REPUTATION, "prt", "trusters"),
        REPORTERS(REPUTATION, "prr", "reporters"),
        RATINGS(MAIN, "pm", "ratings"),
        TRUSTED(RATINGS, "pmt", "trusted"),
        WATCHED(RATINGS, "pmw", "watched"),
        REPORTED(RATINGS, "pmr", "reported"),
        PENDING(RATINGS, "pmp", "pending");

        /** Where Back leads; null for the root. */
        private final Section parent;

        /**
         * Callback action of the button that opens this section. Must start with the
         * profile prefix of {@link TrustCallbackPayloadService} and stay unique.
         */
        private final String code;

        /**
         * Suffix of the locale keys {@code myprofile.button.*} and
         * {@code myprofile.title.*}.
         */
        private final String key;

        public static Optional<Section> fromCode(String code) {
            for (Section section : values()) {
                if (section.code.equals(code)) {
                    return Optional.of(section);
                }
            }
            return Optional.empty();
        }
    }

    private final LocalizationService messages;
    private final TrustCallbackPayloadService callbackPayloads;
    private final LocalRepositories repositories;

    public String buildText(String player, Section section) throws SQLException {
        String profile = messages.format(
                "telegram.command.myprofile.text",
                "trusters", countPlayers(player, Section.TRUSTERS),
                "reporters", countPlayers(player, Section.REPORTERS),
                "trusted", countPlayers(player, Section.TRUSTED),
                "watched", countPlayers(player, Section.WATCHED),
                "reported", countPlayers(player, Section.REPORTED),
                "pending", countPlayers(player, Section.PENDING));

        if (section == Section.MAIN) {
            return profile;
        }
        return profile + "\n\n" + buildPath(section);
    }

    /**
     * @param page                  zero-based page of the section's player list,
     *                              ignored by menu sections
     * @param allowedTelegramUserId the only Telegram user whose presses are
     *                              accepted
     */
    @SuppressWarnings("unchecked")
    public String buildKeyboard(String player, Section section, int page, long allowedTelegramUserId)
            throws SQLException {
        JSONArray rows = new JSONArray();
        addSectionButtons(rows, player, section, allowedTelegramUserId);
        addPlayerButtons(rows, player, section, page, allowedTelegramUserId);
        if (section != Section.MAIN) {
            addBackButtonTo(rows, section.getParent(), allowedTelegramUserId);
        }

        JSONObject keyboard = new JSONObject();
        keyboard.put("inline_keyboard", rows);
        return keyboard.toJSONString();
    }

    /** "Your reputation / Trust you:" */
    private String buildPath(Section section) {
        List<String> titles = new ArrayList<>();
        for (Section step = section; step != Section.MAIN; step = step.getParent()) {
            titles.add(0, messages.format("telegram.command.myprofile.title." + step.getKey()));
        }
        return String.join(" / ", titles) + ":";
    }

    // TODO wtf this is string / json service why the fuck it interacts with the db
    // or its to small interaction to justify a service of its own ?
    private int countPlayers(String player, Section section) throws SQLException {
        return switch (section) {
            case TRUSTERS -> repositories.getTrust().countTrusters(player);
            case REPORTERS -> repositories.getReports().countReporters(player);
            case TRUSTED -> repositories.getTrust().countTrusted(player);
            case REPORTED -> repositories.getReports().countReportedByOwner(player);
            case PENDING -> repositories.getInteractionCounts().countUndecidedInteractors(player);
            case WATCHED -> 0; // TODO: watch relation is not implemented yet
            default -> 0; // MAIN, REPUTATION and RATINGS are menus, they list no players
        };
    }

    private List<String> findPlayers(String player, Section section, int limit, int offset) throws SQLException {
        return switch (section) {
            case TRUSTERS -> repositories.getTrust().findTrusters(player, limit, offset);
            case REPORTERS -> repositories.getReports().findReporters(player, limit, offset);
            case TRUSTED -> repositories.getTrust().findTrusted(player, limit, offset);
            case REPORTED -> repositories.getReports().findReportedByOwner(player, limit, offset);
            case PENDING -> repositories.getInteractionCounts().findUndecidedInteractors(player, limit, offset);
            case WATCHED -> List.of(); // TODO: watch relation is not implemented yet
            default -> List.of(); // MAIN, REPUTATION and RATINGS are menus, they list no players
        };
    }

    @SuppressWarnings("unchecked")
    private void addSectionButtons(JSONArray rows, String player, Section parent, long allowedTelegramUserId)
            throws SQLException {
        for (Section child : Section.values()) {
            if (child.getParent() == parent) {
                rows.add(row(button(
                        // {count} is only in the labels of list sections
                        messages.format(
                                "telegram.command.myprofile.button." + child.getKey(),
                                "count", countPlayers(player, child)),
                        callbackPayloads.encodeProfile(child.getCode(), allowedTelegramUserId, FIRST_PAGE))));
            }
        }
    }

    /**
     * The players of one page as buttons, then the page row. Menu sections add
     * nothing.
     */
    // TODO wtf is the style to pass mutable rows - is that c++ ?
    // why the fuck not return the rows and let the caller add them to the keyboard
    @SuppressWarnings("unchecked")
    private void addPlayerButtons(JSONArray rows, String player, Section section, int page, long allowedTelegramUserId)
            throws SQLException {
        int totalPages = Math.max(1, (countPlayers(player, section) + PAGE_SIZE - 1) / PAGE_SIZE);
        // A button of an older message can point past the end once the list has shrunk.
        int shownPage = Math.min(page, totalPages - 1);

        for (String listed : findPlayers(player, section, PAGE_SIZE, shownPage * PAGE_SIZE)) {
            // TODO: a button per player should open that player's own section
            rows.add(row(button(
                    messages.format("telegram.command.myprofile.player-button", "player", listed),
                    callbackPayloads.encodeNoop())));
        }

        if (totalPages == 1) {
            return; // everything fits on one page: no page row
        }

        JSONArray pageRow = new JSONArray();
        if (shownPage > FIRST_PAGE) {
            pageRow.add(button(
                    messages.format("telegram.button.page-previous"),
                    callbackPayloads.encodeProfile(section.getCode(), allowedTelegramUserId, shownPage - 1)));
        }
        pageRow.add(button((shownPage + 1) + " / " + totalPages, callbackPayloads.encodeNoop()));
        if (shownPage < totalPages - 1) {
            pageRow.add(button(
                    messages.format("telegram.button.page-next"),
                    callbackPayloads.encodeProfile(section.getCode(), allowedTelegramUserId, shownPage + 1)));
        }
        rows.add(pageRow);
    }

    @SuppressWarnings("unchecked")
    private void addBackButtonTo(JSONArray rows, Section target, long allowedTelegramUserId) {
        rows.add(row(button(
                messages.format("telegram.button.back"),
                callbackPayloads.encodeProfile(target.getCode(), allowedTelegramUserId, FIRST_PAGE))));
    }

    @SuppressWarnings("unchecked")
    private JSONArray row(JSONObject button) {
        JSONArray row = new JSONArray();
        row.add(button);
        return row;
    }

    @SuppressWarnings("unchecked")
    private JSONObject button(String text, String callbackData) {
        JSONObject button = new JSONObject();
        button.put("text", text);
        button.put("callback_data", callbackData);
        return button;
    }
}
