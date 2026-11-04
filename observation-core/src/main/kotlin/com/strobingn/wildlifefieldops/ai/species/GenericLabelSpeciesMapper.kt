package com.strobingn.wildlifefieldops.ai.species

/**
 * Restricts a generic on-device labeler (ML Kit's default image labeler, or any
 * ImageNet-style tag list) to the field species list.
 *
 * ML Kit's bundled model has a "Dog" label and no raccoon, skunk, opossum, or
 * squirrel label. Furry mammals, including raccoons, often come back as Dog,
 * sometimes with Mammal / Carnivore / Fur. That must not become a confident dog.
 */
object GenericLabelSpeciesMapper {

    private val mammalAmbiguity = listOf(
        "mammal", "mammals", "carnivore", "carnivores", "animal", "animals",
        "wildlife", "fur", "snout", "creature", "quadruped", "vertebrate",
    )

    private val raccoonLike = listOf(
        "raccoon", "racoon", "procyon", "ringtail", "ringed tail", "ring tailed",
    )

    fun map(labels: List<LabelScore>): SpeciesIdDecision? {
        val explicit = LinkedHashMap<String, Float>()
        val explicitSpecific = LinkedHashMap<String, Boolean>()
        var dogScore = 0f
        var sawDog = false
        var breedOrPet = false
        var mammalAmbiguous = false
        var raccoonAmbiguous = false
        var genericSquirrel = 0f
        var sawGenericSquirrel = false
        var genericBird = 0f
        var sawGenericBird = false
        var genericInsect = 0f
        var sawGenericInsect = false

        for (label in labels) {
            val text = WildlifeSpeciesCatalog.normalize(label.text)
            if (text.isEmpty() || isFoodFalseDog(text)) continue
            val score = label.confidence.coerceIn(0f, 1f)
            if (mammalAmbiguity.any { WildlifeSpeciesCatalog.containsAlias(text, it) }) {
                mammalAmbiguous = true
            }
            if (raccoonLike.any { WildlifeSpeciesCatalog.containsAlias(text, it) }) {
                raccoonAmbiguous = true
            }
            if (isGenericBird(text)) {
                sawGenericBird = true
                genericBird = maxOf(genericBird, score)
            }
            if (isGenericInsect(text)) {
                sawGenericInsect = true
                genericInsect = maxOf(genericInsect, score)
            }
            if (isBreedOrPetCue(text)) breedOrPet = true
            val matched = WildlifeSpeciesCatalog.match(text)
            when {
                matched == null && WildlifeSpeciesCatalog.containsAlias(text, "squirrel") -> {
                    sawGenericSquirrel = true
                    genericSquirrel = maxOf(genericSquirrel, score)
                }
                matched == null -> Unit
                matched.species == WildlifeSpeciesCatalog.DOG -> {
                    sawDog = true
                    dogScore = maxOf(dogScore, score)
                }
                else -> {
                    val name = matched.species.display
                    val prior = explicit[name]
                    if (prior == null || score > prior) explicit[name] = score
                    explicitSpecific[name] = (explicitSpecific[name] ?: false) || matched.specific
                }
            }
        }

        // The evidence lexicon rewrites "squirrel" → "gray squirrel" and "goose" → "canada goose"
        // before this mapper runs. A bare token plus that rewrite is still generic.
        var sawGenericGoose = false
        var genericGoose = 0f
        if (hasExact(labels, "squirrel", "squirrels") && !hasIndependentSquirrel(labels)) {
            listOf(
                WildlifeSpeciesCatalog.GRAY_SQUIRREL.display,
                WildlifeSpeciesCatalog.RED_SQUIRREL.display,
                WildlifeSpeciesCatalog.FLYING_SQUIRREL.display,
            ).forEach {
                explicit.remove(it)
                explicitSpecific.remove(it)
            }
            sawGenericSquirrel = true
            genericSquirrel = maxOf(genericSquirrel, scoreOf(labels, "squirrel", "squirrels"))
        }
        if (hasExact(labels, "goose", "geese") && !hasIndependentCanadaGoose(labels)) {
            explicit.remove(WildlifeSpeciesCatalog.CANADA_GOOSE.display)
            explicitSpecific.remove(WildlifeSpeciesCatalog.CANADA_GOOSE.display)
            sawGenericGoose = true
            genericGoose = scoreOf(labels, "goose", "geese")
        }

        // Bare "Dog", or Dog plus mammal/fur/carnivore, is not a pet-dog ID.
        // This labeler has no raccoon class and uses Dog as the furry-mammal sink.
        // A breed or pet cue without those ambiguity tags can still be Dog.
        val dogBlocked = sawDog && (mammalAmbiguous || raccoonAmbiguous || !breedOrPet)

        if (explicit.isNotEmpty()) {
            val primary = explicit.maxBy { it.value }
            val alternates = buildList {
                explicit.entries
                    .filter { it.key != primary.key }
                    .sortedByDescending { it.value }
                    .forEach { add(SpeciesAlternate(it.key, it.value)) }
                addAll(addFamilyAlternates(primary.key))
                if (sawDog) add(SpeciesAlternate(WildlifeSpeciesCatalog.DOG.display, if (dogBlocked) dogScore.coerceAtMost(0.3f) else dogScore))
            }.distinctBy { it.species }.filter { it.species != primary.key }.take(6)
            val specific = explicitSpecific[primary.key] == true
            val confidence = primary.value
            return SpeciesIdDecision(
                displayLabel = primary.key,
                species = primary.key,
                confidence = confidence,
                confident = specific && confidence >= 0.55f,
                alternates = alternates,
                evidenceNotes = if (sawDog && dogBlocked) {
                    "A generic labeler also said dog. That label is not used while a field species matched."
                } else {
                    ""
                },
            )
        }

        if (sawGenericGoose) {
            return unsure(
                confidence = genericGoose,
                alternates = listOf(WildlifeSpeciesCatalog.CANADA_GOOSE.display),
                notes = "The labeler only said goose. Confirm it is a Canada goose before using that species.",
            )
        }
        if (sawGenericSquirrel) {
            return unsure(
                confidence = genericSquirrel,
                alternates = listOf(
                    WildlifeSpeciesCatalog.GRAY_SQUIRREL.display,
                    WildlifeSpeciesCatalog.RED_SQUIRREL.display,
                    WildlifeSpeciesCatalog.FLYING_SQUIRREL.display,
                ),
                notes = "The labeler only said squirrel. Gray, red, and flying squirrels are separate. Pick one.",
            )
        }
        if (sawGenericBird) {
            return unsure(
                confidence = genericBird,
                alternates = listOf(
                    WildlifeSpeciesCatalog.PIGEON.display,
                    WildlifeSpeciesCatalog.SPARROW.display,
                    WildlifeSpeciesCatalog.STARLING.display,
                    WildlifeSpeciesCatalog.CANADA_GOOSE.display,
                ),
                notes = "The labeler only said bird. Pick pigeon, sparrow, starling, or Canada goose.",
            )
        }
        if (sawGenericInsect) {
            return unsure(
                confidence = genericInsect,
                alternates = listOf(
                    WildlifeSpeciesCatalog.BEES.display,
                    WildlifeSpeciesCatalog.WASPS.display,
                    WildlifeSpeciesCatalog.HORNETS.display,
                ),
                notes = "The labeler only said insect. Pick bees, wasps, or hornets.",
            )
        }
        if (dogBlocked) {
            return unsure(
                confidence = dogScore,
                alternates = listOf(
                    WildlifeSpeciesCatalog.RACCOON.display,
                    WildlifeSpeciesCatalog.SKUNK.display,
                    WildlifeSpeciesCatalog.OPOSSUM.display,
                    WildlifeSpeciesCatalog.FOX.display,
                    WildlifeSpeciesCatalog.COYOTE.display,
                    WildlifeSpeciesCatalog.FERAL_CAT.display,
                    WildlifeSpeciesCatalog.DOG.display,
                ),
                notes = "Generic labels included dog with mammal ambiguity (or dog alone). " +
                    "This on-device model has no raccoon class and often calls raccoons dogs. Pick the species.",
            )
        }
        if (sawDog) {
            return SpeciesIdDecision(
                displayLabel = WildlifeSpeciesCatalog.DOG.display,
                species = WildlifeSpeciesCatalog.DOG.display,
                confidence = dogScore,
                confident = dogScore >= 0.55f,
                alternates = emptyList(),
                evidenceNotes = "Pet-dog cues (breed or pet) were in the generic labels.",
            )
        }
        return null
    }

    fun blocksConfidentDog(texts: Collection<String>): Boolean {
        if (texts.any { isFoodFalseDog(WildlifeSpeciesCatalog.normalize(it)) }) return true
        val decision = map(texts.map { LabelScore(it, 0.9f) }) ?: return false
        return decision.species != WildlifeSpeciesCatalog.DOG.display || !decision.confident
    }

    private fun unsure(
        confidence: Float,
        alternates: List<String>,
        notes: String,
    ): SpeciesIdDecision = SpeciesIdDecision(
        displayLabel = WildlifeSpeciesCatalog.UNSURE,
        species = "",
        confidence = confidence.coerceIn(0.22f, 0.4f),
        confident = false,
        alternates = alternates.map { SpeciesAlternate(it, 0.2f) },
        evidenceNotes = notes,
    )

    private fun addFamilyAlternates(primary: String): List<SpeciesAlternate> {
        val squirrels = listOf(
            WildlifeSpeciesCatalog.GRAY_SQUIRREL.display,
            WildlifeSpeciesCatalog.RED_SQUIRREL.display,
            WildlifeSpeciesCatalog.FLYING_SQUIRREL.display,
        )
        if (primary !in squirrels) return emptyList()
        return squirrels.filter { it != primary }.map { SpeciesAlternate(it, 0.2f) }
    }

    private fun isBreedOrPetCue(text: String): Boolean {
        if (text.isEmpty() || isFoodFalseDog(text)) return false
        if (WildlifeSpeciesCatalog.containsAlias(text, "pet")) return true
        if (WildlifeSpeciesCatalog.containsAlias(text, "puppy") || WildlifeSpeciesCatalog.containsAlias(text, "puppies")) return true
        if (WildlifeSpeciesCatalog.containsAlias(text, "leash") || WildlifeSpeciesCatalog.containsAlias(text, "collar")) return true
        val breedWords = listOf(
            "labrador", "retriever", "shepherd", "poodle", "bulldog", "hound",
            "terrier", "sheepdog", "dalmatian", "boxer", "beagle", "husky", "corgi",
            "rottweiler", "dachshund", "chihuahua", "pug", "spaniel", "collie",
            "shikoku", "basset", "cavalier", "mastiff", "doberman", "pit bull", "mutt",
        )
        return breedWords.any { WildlifeSpeciesCatalog.containsAlias(text, it) }
    }

    private fun isGenericBird(text: String): Boolean {
        val generic = listOf("bird", "birds", "avian", "waterfowl", "duck", "ducks")
        return generic.any { WildlifeSpeciesCatalog.containsAlias(text, it) }
    }

    private fun isGenericInsect(text: String): Boolean =
        WildlifeSpeciesCatalog.containsAlias(text, "insect") ||
            WildlifeSpeciesCatalog.containsAlias(text, "insects")

    private fun isFoodFalseDog(text: String): Boolean =
        text.contains("hot dog") || text.contains("hotdog") || text.contains("corn dog")

    private fun hasExact(labels: List<LabelScore>, vararg tokens: String): Boolean =
        labels.any { WildlifeSpeciesCatalog.normalize(it.text) in tokens }

    private fun scoreOf(labels: List<LabelScore>, vararg tokens: String): Float =
        labels.filter { WildlifeSpeciesCatalog.normalize(it.text) in tokens }
            .maxOfOrNull { it.confidence.coerceIn(0f, 1f) } ?: 0f

    private fun hasIndependentSquirrel(labels: List<LabelScore>): Boolean {
        val rewritten = setOf("gray squirrel", "grey squirrel")
        val specific = listOf(
            "gray squirrel", "grey squirrel", "eastern gray squirrel", "eastern grey squirrel",
            "sciurus carolinensis", "red squirrel", "pine squirrel", "tamiasciurus",
            "american red squirrel", "flying squirrel", "glaucomys",
        )
        return labels.any { label ->
            val text = WildlifeSpeciesCatalog.normalize(label.text)
            text !in rewritten && specific.any { WildlifeSpeciesCatalog.containsAlias(text, it) }
        }
    }

    private fun hasIndependentCanadaGoose(labels: List<LabelScore>): Boolean {
        val specific = listOf("canada goose", "canadian goose", "branta canadensis")
        return labels.any { label ->
            val text = WildlifeSpeciesCatalog.normalize(label.text)
            text != "canada goose" && specific.any { WildlifeSpeciesCatalog.containsAlias(text, it) }
        }
    }
}
