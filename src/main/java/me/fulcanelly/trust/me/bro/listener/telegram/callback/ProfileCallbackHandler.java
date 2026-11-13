package me.fulcanelly.trust.me.bro.listener.telegram.callback;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.tapi.TGBot;
import me.fulcanelly.tgbridge.tapi.events.CallbackQueryEvent;
import me.fulcanelly.trust.me.bro.bootstrap.bundles.LocalRepositories;
import me.fulcanelly.trust.me.bro.listener.telegram.callback.TrustCallbackPayloadService.Payload;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder;
import me.fulcanelly.trust.me.bro.service.text.ProfileMessageBuilder.Button;
import me.fulcanelly.trust.me.bro.service.util.LocalizationService;

/**
 * The /rep screens and what a profile button turns the message into.
 *
 * Shared by the command (first message) and {@link TrustCallbackHandler} (edits
 * when a button is pressed), so both always show the same thing. It reads the
 * repositories and decides the text and the buttons; {@link ProfileMessageBuilder}
 * only turns them into strings and JSON.
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
 *
 * A player button of a list opens that player's own screen, "Path / To / Section /
 * Player:" with the interactions between the two and a Back to the same page of the
 * list. It is not a Section: which list it hangs under is only known from the press.
 */
@RequiredArgsConstructor
public final class ProfileCallbackHandler {

    public static final int FIRST_PAGE = 0;

    private static final int PAGE_SIZE = 5;

    /** Telegram refuses the whole keyboard when one button's callback data is longer. */
    private static final int CALLBACK_DATA_LIMIT_BYTES = 64;

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
         * Suffix of the locale key {@code myprofile.title.*}: the section's button
         * label and its step in the "Path / To / Section:" line.
         */
        private final String key;

        /** A menu opens other sections, any other section opens its players. */
        public boolean isMenu() {
            for (Section section : values()) {
                if (section.parent == this) {
                    return true;
                }
            }
            return false;
        }

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
    private final ProfileMessageBuilder builder;

    /** Only {@link #onProfileAction} uses it, to edit the message the button is under. */
    private final TGBot bot;

    /**
     * Profile buttons re-render the /rep message in place: the pressed
     * button's section (the target of Back is just another section) of the player
     * the button carries, or the screen of one listed player when the button
     * carries one. Only the author of the command may press them.
     */
    public void onProfileAction(CallbackQueryEvent event, Payload payload) throws SQLException {
        long telegramUserId = event.getFrom().getId();

        Optional<Section> section = Section.fromCode(payload.getAction());
        // guard against a button of
        // an button that was never valid for this message
        if (section.isEmpty() || payload.getPage().isEmpty() || payload.getProfilePlayer().isEmpty()) {
            event.answer(messages.format("callback.bad-action"));
            return;
        }


        Optional<Long> allowedUser = payload.getAllowedTelegramUserId();
        if (allowedUser.isEmpty() || allowedUser.get() != telegramUserId) {
            event.answer(messages.format("callback.not-your-profile"));
            return;
        }

        String profilePlayer = payload.getProfilePlayer().get();
        Optional<String> listedPlayer = payload.getListedPlayer();

        String text;
        String keyboard;

        if (listedPlayer.isPresent()) {
            text = buildPlayerText(profilePlayer, section.get(), listedPlayer.get());
            keyboard = buildPlayerKeyboard(profilePlayer, section.get(), payload.getPage().get(), telegramUserId);
        } else {
            text = buildText(profilePlayer, section.get());
            keyboard = buildKeyboard(profilePlayer, section.get(), payload.getPage().get(), telegramUserId);
        }

        bot.editMessage(
                event.getMessage().getChat().getId(),
                event.getMessage().getMsgId(),
                text,
                keyboard);
        event.answer("");
    }

    public String buildText(String player, Section section) throws SQLException {
        return builder.profileText(
                player,
                countPlayers(player, Section.TRUSTERS),
                countPlayers(player, Section.REPORTERS),
                countPlayers(player, Section.TRUSTED),
                countPlayers(player, Section.WATCHED),
                countPlayers(player, Section.REPORTED),
                countPlayers(player, Section.PENDING),
                pathTitles(section));
    }

    /**
     * The screen of one player picked from a section's list: how many
     * {@code interaction_counts} rows each of the two has towards the other.
     */
    public String buildPlayerText(String player, Section section, String listedPlayer) throws SQLException {
        var interactionStats = repositories.getInteractionStats();

        return builder.playerText(
                player,
                listedPlayer,
                pathTitles(section),
                interactionStats.countInteractions(listedPlayer, player),
                interactionStats.countInteractions(player, listedPlayer));
    }

    /**
     * @param page                  zero-based page of the section's player list,
     *                              ignored by menu sections
     * @param allowedTelegramUserId the only Telegram user whose presses are
     *                              accepted
     */
    public String buildKeyboard(String player, Section section, int page, long allowedTelegramUserId)
            throws SQLException {
        List<List<Button>> rows = new ArrayList<>();
        rows.addAll(sectionButtons(player, section, allowedTelegramUserId));
        rows.addAll(playerButtons(player, section, page, allowedTelegramUserId));
        if (section != Section.MAIN) {
            rows.add(backButtonTo(player, section.getParent(), FIRST_PAGE, allowedTelegramUserId));
        }
        return builder.keyboard(rows);
    }

    /**
     * Only Back, to the page of the list the player was opened from.
     *
     * @param page zero-based page of the section's list that was showing
     */
    public String buildPlayerKeyboard(String player, Section section, int page, long allowedTelegramUserId) {
        return builder.keyboard(List.of(backButtonTo(player, section, page, allowedTelegramUserId)));
    }

    /** Titles from the root's child down to the section, empty for the root. */
    private List<String> pathTitles(Section section) {
        List<String> titles = new ArrayList<>();
        for (Section step = section; step != Section.MAIN; step = step.getParent()) {
            titles.add(0, messages.format("telegram.command.myprofile.title." + step.getKey()));
        }
        return titles;
    }

    private int countPlayers(String player, Section section) throws SQLException {
        return switch (section) {
            case TRUSTERS -> repositories.getTrust().countTrusters(player);
            case REPORTERS -> repositories.getReports().countReporters(player);
            case TRUSTED -> repositories.getTrust().countTrusted(player);
            case REPORTED -> repositories.getReports().countReportedByOwner(player);
            case PENDING -> repositories.getInteractionStats().countUndecidedInteractors(player);
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
            case PENDING -> repositories.getInteractionStats().findUndecidedInteractors(player, limit, offset);
            case WATCHED -> List.of(); // TODO: watch relation is not implemented yet
            default -> List.of(); // MAIN, REPUTATION and RATINGS are menus, they list no players
        };
    }

    /** One row per child section; a list section's label says how many players are in it. */
    private List<List<Button>> sectionButtons(String player, Section parent, long allowedTelegramUserId)
            throws SQLException {
        List<List<Button>> rows = new ArrayList<>();
        for (Section child : Section.values()) {
            if (child.getParent() != parent) {
                continue;
            }

            String label = messages.format("telegram.command.myprofile.title." + child.getKey());
            if (!child.isMenu()) {
                label = messages.format(
                        "telegram.command.myprofile.button-with-count",
                        "title", label,
                        "count", countPlayers(player, child));
            }
            rows.add(List.of(new Button(
                    label,
                    callbackPayloads.encodeProfile(child.getCode(), allowedTelegramUserId, FIRST_PAGE, player))));
        }
        return rows;
    }

    /**
     * One row per player of the page, then the page row. Menu sections have no
     * players, so no rows.
     */
    private List<List<Button>> playerButtons(String player, Section section, int page, long allowedTelegramUserId)
            throws SQLException {
        int totalPages = Math.max(1, (countPlayers(player, section) + PAGE_SIZE - 1) / PAGE_SIZE);
        // A button of an older message can point past the end once the list has shrunk.
        int shownPage = Math.min(page, totalPages - 1);

        List<List<Button>> rows = new ArrayList<>();
        for (String listed : findPlayers(player, section, PAGE_SIZE, shownPage * PAGE_SIZE)) {
            String open = callbackPayloads.encodeProfile(
                    section.getCode(), allowedTelegramUserId, shownPage, player, listed);
            if (open.getBytes(StandardCharsets.UTF_8).length > CALLBACK_DATA_LIMIT_BYTES) {
                // Two long names do not fit: the button stays but does nothing, instead of
                // Telegram refusing the whole keyboard. Real nicknames are far shorter.
                open = callbackPayloads.encodeNoop();
            }
            rows.add(List.of(new Button(
                    messages.format("telegram.command.myprofile.player-button", "player", listed),
                    open)));
        }

        if (totalPages == 1) {
            return rows; // everything fits on one page: no page row
        }

        List<Button> pageRow = new ArrayList<>();
        if (shownPage > FIRST_PAGE) {
            pageRow.add(new Button(
                    messages.format("telegram.button.page-previous"),
                    callbackPayloads.encodeProfile(section.getCode(), allowedTelegramUserId, shownPage - 1, player)));
        }
        pageRow.add(new Button((shownPage + 1) + " / " + totalPages, callbackPayloads.encodeNoop()));
        if (shownPage < totalPages - 1) {
            pageRow.add(new Button(
                    messages.format("telegram.button.page-next"),
                    callbackPayloads.encodeProfile(section.getCode(), allowedTelegramUserId, shownPage + 1, player)));
        }
        rows.add(pageRow);
        return rows;
    }

    private List<Button> backButtonTo(String player, Section target, int page, long allowedTelegramUserId) {
        return List.of(new Button(
                messages.format("telegram.button.back"),
                callbackPayloads.encodeProfile(target.getCode(), allowedTelegramUserId, page, player)));
    }
}
