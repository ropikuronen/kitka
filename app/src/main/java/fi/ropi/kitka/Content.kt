package fi.ropi.kitka

/** All the fixed text: studies, creator advice. Numbers and sources must not be changed by hand. */
object Content {

    class Study(val title: String, val number: String, val text: String, val cite: String, val url: String)

    val WHY_IT_WORKS = listOf(
        Study("A pause before the app opens", "57 % fewer opens in 6 weeks",
            "280 people. They closed the app 36 % of the times the pause appeared and tried to open it 37 % less by week 6.",
            "Grüning et al. 2023, PNAS", "https://doi.org/10.1073/pnas.2213114120"),
        Study("Close is the big button", "The strongest part of the pause",
            "The option to close did the most. The waiting time helped too, but less. A message asking you to reflect had no effect, so the pause has a short note from the creator, not a reflection prompt.",
            "Grüning et al. 2023, PNAS", "https://doi.org/10.1073/pnas.2213114120"),
        Study("Choose a time before you go in", "d = 0.27 on behaviour, 642 tests",
            "Deciding in advance exactly when and how long helps people follow through. Small but real. Not tested on social media yet.",
            "Gollwitzer & Sheeran 2006; Sheeran et al. 2024", "https://doi.org/10.1016/S0065-2601(06)38002-1"),
        Study("Rules only loosen after 24 h", "31 % of use is self-control problems",
            "Letting people set limits on their future use clearly reduced use. Self-control problems explained about 31 % of social media use.",
            "Allcott, Gentzkow & Song 2022, AER", "https://doi.org/10.1257/aer.20210867"),
        Study("Grayscale inside selected apps", "Cut screen time right away",
            "112 students. Grayscale lowered screen time at once, more than time limits did.",
            "Zimmermann & Sobolev 2023", "https://doi.org/10.1089/cyber.2022.0027"),
        Study("Timer and Daily Proof", "d = 0.40, 138 studies",
            "Tracking progress toward a goal helps you reach it, more when it is written down or shared. Mostly health goals. Tracking alone did not cut screen time, so it is paired with the hard stop.",
            "Harkin et al. 2016, Psychological Bulletin", "https://doi.org/10.1037/bul0000025"),
        Study("What is not proven", "Read this one",
            "Most studies last weeks. Nobody has shown a tool like this makes you stop for good. People who switched the pause off went back to old use quickly. It works while it is on.",
            "Haliburton et al. 2024, CHI", "https://doi.org/10.1145/3613904.3642370"),
    )

    val WHY_YOU_REACH = listOf(
        Study("It is a habit, not a choice", "Most checks last under 30 seconds",
            "Phone checks are short, automatic and triggered by situations like boredom or commuting. People called it an annoyance, not an addiction.",
            "Oulasvirta et al. 2012, Aalto / HIIT", "https://doi.org/10.1007/s00779-011-0412-2"),
        Study("Boredom", "r = .38 across many studies",
            "People who get bored easily have more problematic phone use. A moderate link, not proof that boredom causes it.",
            "Meta-analysis, Psychological Reports 2026", "https://doi.org/10.1177/00332941261459995"),
    )

    val WHAT_YOU_GET = listOf(
        Study("Less loneliness and depression", "3 weeks, 143 students",
            "Students who limited Facebook, Instagram and Snapchat to 10 minutes each a day felt less lonely and less depressed than those who kept using them as usual.",
            "Hunt et al. 2018, J. Social and Clinical Psychology", "https://doi.org/10.1521/jscp.2018.37.10.751"),
        Study("More time with people", "4 weeks off Facebook",
            "People paid to deactivate Facebook spent more time offline with friends and family and reported higher well-being. They also used it less afterwards.",
            "Allcott et al. 2020, AER", "https://doi.org/10.1257/aer.20190658"),
        Study("Better sleep", "30 min phone-free before bed",
            "In a small trial (38 people), no phone for 30 minutes before bed meant falling asleep faster and sleeping longer after 4 weeks.",
            "He et al. 2020, PLOS ONE", "https://doi.org/10.1371/journal.pone.0228756"),
    )

    // Pause screen (creator's own words).
    const val PAUSE_HEAD = "This feels uncomfortable. And that's okay."
    val PAUSE_MORE = listOf(
        "Don't run from it, instead face it. It's not the end of the world, and it will pass. You know this.",
        "Boredom, restlessness, and that itch to check something. It feels urgent, but it isn't dangerous. Realize this.",
        "Every time you sit with it instead of escaping, you become stronger.",
    )

    // From the creator page.
    const val CREATOR_HEAD = "An app doesn't make you stop scrolling. Your will and discipline do."
    val CREATOR_MORE = listOf(
        "If you want to change, the change has to come from within.",
        "Ask yourself: why am I running from feeling bored or anxious? Why can't I face my feelings?",
        "Your mentality has to change, and you have to be willing to face discomfort. Otherwise you'll just run away, like you've done up to this point.",
    )
    const val CREATOR_LAST = "Face the discomfort."

    class Tip(val title: String, val sub: String?)
    val TIPS = listOf(
        Tip("Find the worst versions of yourself.", "10 % of your bad decisions cause 80 % of the destruction. Your History tab shows yours."),
        Tip("IF that happens, THEN I'll do this.", "Never let the bad versions negotiate. The rule stands."),
        Tip("You have about 1 second.", "Then the decision making crumbles and the negotiating starts."),
        Tip("A thought is just a thought. A feeling is just a feeling.", "In reality, it will not harm you. Unmask it."),
        Tip("Look up on the bus.", "Count how many are stuck in their phones. Don't be one of them."),
    )
    const val TIP_LAST_HEAD = "There's no quick fix."
    const val TIP_LAST_SUB = "Choose not to scroll. Hundreds of times a day. You've got this."

    const val SUPPORT_URL = "https://buymeacoffee.com/ropi"
}
