/**
 * Northeast US nuisance species seeded into public.wildlife_reference.
 * Taxon ids were resolved from api.inaturalist.org/v1/taxa (species rank).
 * Place ids are the iNaturalist state places for NY, NJ, CT, MA, PA, VT, NH, ME, RI.
 */

export const PHOTOS_PER_SPECIES = 150;
export const SIGNS_PER_SPECIES = 30;
export const SIGN_TYPES = [
  { signType: "track", termId: 22, termValueId: 26 },
  { signType: "scat", termId: 22, termValueId: 25 },
];

export const NORTHEAST_PLACE_IDS = [48, 51, 49, 2, 42, 47, 41, 17, 8];

export const TARGETS = [
  target(41663, "Procyon lotor", "Raccoon", "Raccoon"),
  target(46017, "Sciurus carolinensis", "Gray squirrel", "Gray squirrel"),
  target(46260, "Tamiasciurus hudsonicus", "Red squirrel", "Red squirrel"),
  target(46272, "Glaucomys volans", "Southern flying squirrel", "Flying squirrel"),
  target(46266, "Glaucomys sabrinus", "Northern flying squirrel", "Flying squirrel"),
  target(41880, "Mephitis mephitis", "Striped skunk", "Skunk"),
  target(42652, "Didelphis virginiana", "Virginia opossum", "Opossum"),
  target(46095, "Marmota monax", "Groundhog", "Groundhog"),
  target(46217, "Tamias striatus", "Eastern chipmunk", "Chipmunk"),
  target(40509, "Eptesicus fuscus", "Big brown bat", "Bat"),
  target(40346, "Myotis lucifugus", "Little brown bat", "Bat"),
  target(44576, "Rattus norvegicus", "Norway rat", "Rat"),
  target(44705, "Mus musculus", "House mouse", "Mouse"),
  target(44396, "Peromyscus maniculatus", "Deer mouse", "Mouse"),
  target(3017, "Columba livia", "Rock pigeon", "Pigeon"),
  target(14850, "Sturnus vulgaris", "European starling", "Starling"),
  target(13858, "Passer domesticus", "House sparrow", "Sparrow"),
  target(42069, "Vulpes vulpes", "Red fox", "Fox"),
  target(42051, "Canis latrans", "Coyote", "Coyote"),
  target(42223, "Odocoileus virginianus", "White-tailed deer", "Deer"),
  target(43111, "Sylvilagus floridanus", "Eastern cottontail", "Eastern cottontail"),
  target(28362, "Thamnophis sirtalis", "Common garter snake", "Snake"),
  target(515419, "Lampropeltis triangulum", "Eastern milksnake", "Snake"),
  target(29305, "Nerodia sipedon", "Common watersnake", "Snake"),
  target(1661279, "Pantherophis alleghaniensis", "Central ratsnake", "Snake"),
  target(28562, "Storeria dekayi", "Dekay's brownsnake", "Snake"),
  target(26575, "Diadophis punctatus", "Ring-necked snake", "Snake"),
  target(912622, "Agkistrodon contortrix", "Eastern copperhead", "Snake"),
  target(30746, "Crotalus horridus", "Timber rattlesnake", "Snake"),
  target(146314, "Opheodrys vernalis", "Smooth greensnake", "Snake"),
  target(29925, "Heterodon platirhinos", "Eastern hognose snake", "Snake"),
];

function target(taxonId, scientific, commonName, fieldLabel) {
  return { taxonId, scientific, commonName, fieldLabel };
}

export function expectedLoad(targets = TARGETS) {
  const species = targets.length;
  const organism = species * PHOTOS_PER_SPECIES;
  const signs = species * SIGN_TYPES.length * SIGNS_PER_SPECIES;
  return {
    species,
    photosPerSpecies: PHOTOS_PER_SPECIES,
    signsPerSpecies: SIGNS_PER_SPECIES,
    organism,
    signs,
    total: organism + signs,
  };
}
