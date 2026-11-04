package com.strobingn.wildlifefieldops.ai.species

/**
 * Species a Bronx nuisance-wildlife technician can record from a photo.
 * Cloud and on-device results are both forced onto this list.
 */
object WildlifeSpeciesCatalog {

    const val UNSURE = "Unsure - pick species"

    data class Species(
        val display: String,
        val aliases: List<Alias>,
    )

    data class Alias(val text: String, val specific: Boolean = true)

    val RACCOON = species("Raccoon", "raccoon", "racoon", "procyon", "procyon lotor")
    val SKUNK = species("Skunk", "skunk", "mephitis")
    val OPOSSUM = species("Opossum", "opossum", "possum", "didelphis")
    val GRAY_SQUIRREL = species(
        "Gray squirrel",
        "gray squirrel", "grey squirrel",
        "eastern gray squirrel", "eastern grey squirrel",
        "sciurus carolinensis",
    )
    val RED_SQUIRREL = species(
        "Red squirrel",
        "red squirrel", "pine squirrel", "tamiasciurus", "american red squirrel",
    )
    val FLYING_SQUIRREL = species("Flying squirrel", "flying squirrel", "glaucomys")
    val GROUNDHOG = species("Groundhog", "groundhog", "woodchuck", "ground hog", "marmota monax", "marmot")
    val CHIPMUNK = species("Chipmunk", "chipmunk", "tamias")
    val RAT = species("Rat", "rat", "rats", "norway rat", "roof rat", "brown rat", "black rat")
    val MOUSE = species("Mouse", "mouse", "mice", "house mouse")
    val BAT = species("Bat", "bat", "bats", "chiroptera", "flying fox")
    val PIGEON = species("Pigeon", "pigeon", "pigeons", "rock dove", "rock pigeon")
    val SPARROW = species("Sparrow", "sparrow", "sparrows", "house sparrow")
    val STARLING = species("Starling", "starling", "starlings", "european starling")
    val CANADA_GOOSE = speciesAliases(
        "Canada goose",
        Alias("canada goose"),
        Alias("canadian goose"),
        Alias("branta canadensis"),
        Alias("goose", specific = false),
        Alias("geese", specific = false),
    )
    val FERAL_CAT = species(
        "Feral cat",
        "feral cat", "stray cat", "domestic cat", "cat", "cats", "kitten", "kittens",
    )
    val FOX = species("Fox", "fox", "red fox", "gray fox", "grey fox")
    val COYOTE = species("Coyote", "coyote", "coyotes")
    val DEER = species("Deer", "deer", "white-tailed deer", "white tailed deer", "whitetail", "whitetail deer")
    val COTTONTAIL = species(
        "Eastern cottontail",
        "eastern cottontail", "cottontail", "sylvilagus floridanus", "rabbit", "bunny",
    )
    val SNAKE = species("Snake", "snake", "snakes", "serpent", "garter snake", "rattlesnake")
    val BEES = species("Bees", "bee", "bees", "honeybee", "honey bee", "bumblebee", "bumble bee")
    val WASPS = species("Wasps", "wasp", "wasps", "yellowjacket", "yellow jacket", "yellow-jacket")
    val HORNETS = species("Hornets", "hornet", "hornets", "bald-faced hornet", "baldfaced hornet")
    val DOG = species(
        "Dog",
        "dog", "dogs", "canine", "canines", "puppy", "puppies",
        "labrador", "retriever", "shepherd", "poodle", "bulldog", "hound",
        "terrier", "sheepdog", "dalmatian", "boxer", "beagle", "husky", "corgi",
        "rottweiler", "dachshund", "chihuahua", "pug", "spaniel", "collie",
        "shikoku", "basset", "cavalier", "mastiff", "doberman", "pit bull", "mutt",
    )
    val UNKNOWN = Species("Unknown", emptyList())

    /** Technician picker order. Unknown is a model output, not a chip. */
    val picker: List<Species> = listOf(
        RACCOON, SKUNK, OPOSSUM,
        GRAY_SQUIRREL, RED_SQUIRREL, FLYING_SQUIRREL,
        GROUNDHOG, CHIPMUNK, RAT, MOUSE, BAT,
        PIGEON, SPARROW, STARLING, CANADA_GOOSE,
        FERAL_CAT, FOX, COYOTE, DEER, COTTONTAIL, SNAKE,
        BEES, WASPS, HORNETS, DOG,
    )

    val pickerLabels: List<String> = picker.map { it.display }

    private val matchable: List<Species> = picker

    fun visionSystemPrompt(): String = """
You identify the animal in one nuisance-wildlife field photo for a company in the Bronx, New York.
Return JSON only, with no markdown, in exactly this shape:
{"species":"","confidence":0.0,"alternates":[{"species":"","confidence":0.0}],"evidence_notes":""}
species must be exactly one of: ${pickerLabels.joinToString(", ")}, Unknown.
Groundhog and woodchuck are Groundhog. Gray, red, and flying squirrels are different species.
Eastern cottontail is the rabbit on this list.
Bees, Wasps, and Hornets are different species. Dog means a pet dog, not a raccoon, fox, coyote, skunk, or opossum.
Raccoons have a black mask and a ringed tail. Do not call a raccoon a dog.
If you cannot tell, use Unknown and put the closest list entries in alternates.
confidence is a number from 0 to 1. At most 3 alternates, chosen from the same list, excluding the chosen species.
evidence_notes is one short sentence naming visible traits. Do not invent laws, prices, or a service.
""".trimIndent()

    const val VISION_USER_PROMPT =
        "Identify the animal in this photo. Choose only from the allowed species list."

    fun normalize(raw: String): String =
        raw.trim().lowercase().replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")

    fun match(text: String): Match? {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return null
        if (isEquipmentFalseAnimal(normalized)) return null
        var best: Match? = null
        for (species in matchable) {
            for (alias in species.aliases) {
                val aliasText = normalize(alias.text)
                if (aliasText.isEmpty() || !containsAlias(normalized, aliasText)) continue
                val current = best
                if (current == null || aliasText.length > current.aliasLength) {
                    best = Match(species, alias.specific, aliasText.length)
                }
            }
        }
        return best
    }

    /** Map a cloud or typed label onto the list. Unknown stays Unknown. */
    fun canonicalize(raw: String): Species? {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null
        if (normalized == "unknown" || normalized == "unsure" || normalized == normalize(UNSURE)) return UNKNOWN
        return match(normalized)?.species
    }

    data class Match(val species: Species, val specific: Boolean, val aliasLength: Int)

    fun containsAlias(text: String, alias: String): Boolean {
        val normalized = normalize(text)
        val needle = normalize(alias)
        if (needle.isEmpty()) return false
        var index = normalized.indexOf(needle)
        while (index >= 0) {
            val before = if (index == 0) ' ' else normalized[index - 1]
            val afterIndex = index + needle.length
            val after = if (afterIndex >= normalized.length) ' ' else normalized[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            index = normalized.indexOf(needle, index + 1)
        }
        return false
    }

    private fun isEquipmentFalseAnimal(text: String): Boolean {
        if (containsAlias(text, "bat") && (
                text.contains("cone") || text.contains("valve") || text.contains("trap")
                )
        ) {
            return true
        }
        return false
    }

    private fun species(display: String, vararg aliases: String): Species =
        Species(display, aliases.map { Alias(it) })

    private fun speciesAliases(display: String, vararg aliases: Alias): Species =
        Species(display, aliases.toList())
}
