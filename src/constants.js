/**
 * Wildlife Whisperer FieldOps — Application Constants
 * All lookup tables, enums, and static data.
 */

// ─── Species ───
export const SPECIES = [
  'Raccoon',
  'Grey Squirrel',
  'Red Squirrel',
  'Flying Squirrel',
  'Bat',
  'Skunk',
  'Groundhog',
  'Bird',
  'Snake',
  'Opossum',
  'Rodent',
  'Mouse',
  'Rat',
  'Carpenter Bee',
  'Other'
];

export const SPECIES_ICONS = {
  Raccoon: '🦝',
  'Grey Squirrel': '🐿️',
  'Red Squirrel': '🐿️',
  'Flying Squirrel': '🦇',
  Bat: '🦇',
  Skunk: '🦨',
  Groundhog: '🦫',
  Bird: '🐦',
  Snake: '🐍',
  Opossum: '🦡',
  Rodent: '🐁',
  Mouse: '🐁',
  Rat: '🐀',
  'Carpenter Bee': '🐝',
  Other: '🐾'
};

// ─── Statuses ───
export const STATUSES = [
  'Active',
  'Scheduled',
  'Waiting On Customer',
  'Trapping',
  'Exclusion',
  'Repair',
  'Warranty',
  'Closed',
  'Cancelled'
];

export const INSPECTION_STATUSES = ['Pending', 'Scheduled', 'Completed', 'No Show', 'Converted'];
export const INSPECTION_STATUS_STYLES = {
  Pending: 'pending',
  Scheduled: 'scheduled',
  Completed: 'completed',
  'No Show': 'cancelled',
  Converted: 'completed'
};

export const STATUS_STYLES = {
  Active: 'active',
  Scheduled: 'scheduled',
  Closed: 'closed',
  Trapping: 'trapping',
  Repair: 'repair',
  'Waiting On Customer': 'scheduled',
  Exclusion: 'active',
  Warranty: 'active',
  Cancelled: 'closed'
};

export const STATUS_COLORS = {
  Active: '#22c55e',
  Scheduled: '#3b82f6',
  'Waiting On Customer': '#fbbf24',
  Trapping: '#f59e0b',
  Exclusion: '#8b5cf6',
  Repair: '#ef4444',
  Warranty: '#06b6d4',
  Closed: '#8a8a9e',
  Cancelled: '#6b7280'
};

// ─── Priorities ───
export const PRIORITIES = ['Low', 'Normal', 'High', 'Critical'];

// ─── Visit Types ───
export const VISIT_TYPES = [
  'Inspection',
  'Trap Set',
  'Trap check',
  'Exclusion',
  'Repair',
  'Warranty Follow-Up',
  'Other'
];

// ─── Repair Statuses ───
export const REPAIR_STATUSES = ['Open', 'Sealed', 'Needs Repair', 'Warranty Covered'];

// ─── Severities ───
export const SEVERITIES = ['Low', 'Medium', 'High', 'Critical'];

// ─── Photo Tags ───
export const PHOTO_TAGS = [
  'Before',
  'Entry Point',
  'Damage',
  'Trap Placement',
  'Droppings / Evidence',
  'Repair During',
  'After',
  'Warranty',
  'Customer signature'
];

// ─── Services with pricing ───
export const SERVICES = [
  { name: 'Inspection', price: 125 },
  { name: 'Inspection photography', price: 75 },
  { name: 'One-way set / one-way door', price: 225 },
  { name: 'Bird gel', price: 125 },
  { name: 'Sheet metal work', price: 35 },
  { name: 'Caulking', price: 12 },
  { name: 'Stainless steel mesh', price: 45 },
  { name: 'Hardware cloth', price: 35 },
  { name: 'Exclusion repair', price: 150 },
  { name: 'Soffit / fascia repair', price: 225 },
  { name: 'Ridge vent guard', price: 300 },
  { name: 'Chimney cap', price: 350 },
  { name: 'Gable vent screening', price: 175 },
  { name: 'Foundation gap sealing', price: 95 },
  { name: 'Cleanup / sanitation', price: 250 },
  { name: 'Warranty follow-up', price: 125 }
];

// ─── Estimate templates ───
export const ESTIMATE_TEMPLATES = {
  raccoon_attic: {
    label: 'Raccoon Attic',
    species: 'Raccoon',
    issue:
      'Raccoon activity in attic. Entry point suspected near roofline/soffit. Need inspection, exclusion, and one-way door.',
    service: 'Exclusion repair',
    price: 150,
    qty: 10
  },
  squirrel_soffit: {
    label: 'Squirrel Soffit',
    species: 'Grey Squirrel',
    issue: 'Squirrel chewing into soffit. Entry point visible. Need exclusion and repair.',
    service: 'Soffit / fascia repair',
    price: 225,
    qty: 1
  },
  bat_exclusion: {
    label: 'Bat Exclusion',
    species: 'Bat',
    issue: 'Bat droppings in attic. Need full bat exclusion, one-way doors, and guano cleanup.',
    service: 'One-way set / one-way door',
    price: 225,
    qty: 3
  },
  groundhog_dig: {
    label: 'Groundhog Burrow',
    species: 'Groundhog',
    issue: 'Groundhog burrowing under shed/deck. Need trapping and exclusion.',
    service: 'Inspection',
    price: 125,
    qty: 1
  },
  bird_vent: {
    label: 'Bird in Vent',
    species: 'Bird',
    issue: 'Bird nest in dryer vent. Need vent guard installation and cleanup.',
    service: 'Gable vent screening',
    price: 175,
    qty: 1
  },
  skunk_under: {
    label: 'Skunk Under Deck',
    species: 'Skunk',
    issue: 'Skunk living under deck. Need trapping and deck exclusion.',
    service: 'Foundation gap sealing',
    price: 95,
    qty: 1
  }
};

// ─── Species hints for AI assistant ───
export const SPECIES_HINTS = {
  Raccoon: 'Attic/chimney/latrine patterns; inspect roof access and soffits.',
  'Grey Squirrel': 'Larger attic nesting, fascia/gable vent risk.',
  'Red Squirrel': 'Aggressive chewing, cone caches, repeat entry attempts.',
  'Flying Squirrel': 'Night activity, colonies, wall/attic movement.',
  Bat: 'Roost timing, guano, staining, legal exclusion window.',
  Skunk: 'Den and odor zones around decks/sheds.',
  Groundhog: 'Burrow/foundation/deck undermining risk.',
  Bird: 'Nest in vents, soffits, chimneys; check for mites.',
  Snake: 'Entry through foundation gaps, cool damp areas.',
  Opossum: 'Under decks/sheds, nocturnal, hiss/bluff defense.',
  Rodent: 'Droppings, gnaw marks, entry through small gaps.',
  Mouse: 'Small droppings, kitchen/ pantry activity.',
  Rat: 'Larger droppings, burrows, grease marks along runs.',
  'Carpenter Bee': 'Fascia/deck recurrence and residual treatment zones.',
  Other: 'Track behavior, seasonality, recurrence.'
};

// ─── Navigation pages ───
export const PAGES = [
  { id: 'dashboard', label: 'Dashboard', icon: '🏠' },
  { id: 'jobs', label: 'Jobs', icon: '🦝' },
  { id: 'gps', label: 'GPS', icon: '📍' },
  { id: 'ai', label: 'AI', icon: '🧠' },
  { id: 'metrics', label: 'Metrics', icon: '📊' }
];

export const DRAWER_PAGES = [
  { id: 'dashboard', label: '🏠 Dashboard' },
  { id: 'jobs', label: '🦝 Jobs' },
  { id: 'inspections', label: '🔍 Inspections' },
  { id: 'schedule', label: '📅 Schedule' },
  { id: 'gps', label: '📍 GPS Map' },
  { id: 'route', label: '🗺️ Route Optimizer' },
  { id: 'estimate', label: '💵 Estimator' },
  { id: 'ai', label: '🧠 AI Assistant' },
  { id: 'customers', label: '👥 Customers' },
  { id: 'photos', label: '📸 Photos' },
  { id: 'expenses', label: '💰 Expenses' },
  { id: 'inventory', label: '📦 Inventory' },
  { id: 'equipment', label: '🔧 Equipment' },
  { id: 'metrics', label: '📊 Metrics' },
  { id: 'settings', label: '⚙️ Settings' }
];

// ─── Bottom nav items ───
export const BOTTOM_NAV = [
  { id: 'dashboard', icon: '🏠', label: 'Home' },
  { id: 'jobs', icon: '🦝', label: 'Jobs' },
  { id: 'inspections', icon: '🔍', label: 'Inspect' },
  { id: 'schedule', icon: '📅', label: 'Schedule' },
  { id: 'gps', icon: '📍', label: 'GPS' }
];

// ─── Base prices for estimates by species ───
export const BASE_PRICES = {
  Bat: 950,
  Raccoon: 650,
  'Grey Squirrel': 550,
  'Red Squirrel': 575,
  'Flying Squirrel': 750,
  Skunk: 450,
  Groundhog: 450,
  'Carpenter Bee': 350
};

// ─── Severity multipliers ───
export const SEVERITY_MULTIPLIERS = {
  Low: 1,
  Medium: 1.35,
  High: 1.8,
  Critical: 2.4
};

// ─── Default tax rate ───
export const DEFAULT_TAX_RATE = 0.08;

// ─── App version ───
export const APP_VERSION = '3.0.0';

// ─── Expense Categories ───
export const EXPENSE_CATEGORIES = [
  'Fuel',
  'Supplies',
  'Equipment',
  'Permits',
  'Vehicle',
  'Insurance',
  'Marketing',
  'Other'
];

// ─── Inventory Categories ───
export const INVENTORY_CATEGORIES = [
  'Traps',
  'Exclusion Materials',
  'Bait',
  'Tools',
  'Safety Gear',
  'Cleaning Supplies',
  'Other'
];

// ─── Equipment Types ───
export const EQUIPMENT_TYPES = ['Truck', 'Trailer', 'Ladder', 'Trap', 'Camera', 'Tool', 'Safety Gear', 'Other'];

// ─── Communication Types ───
export const COMMUNICATION_TYPES = ['Call', 'Email', 'Text', 'In-Person Visit'];

// ─── Weather condition icons ───
export const WEATHER_ICONS = {
  Clear: '☀️',
  Clouds: '☁️',
  Rain: '🌧️',
  Drizzle: '🌦️',
  Thunderstorm: '⛈️',
  Snow: '🌨️',
  Mist: '🌫️',
  Fog: '🌫️',
  Haze: '🌫️',
  Smoke: '🌫️',
  Dust: '🌫️',
  Sand: '🌫️',
  Tornado: '🌪️',
  Squall: '💨'
};

// ─── Job Completion Checklist Items ───
export const DEFAULT_CHECKLIST = [
  { id: 'inspection', label: 'Initial inspection completed', done: false },
  { id: 'entry_points', label: 'Entry points identified and sealed', done: false },
  { id: 'traps', label: 'Traps set / one-way door installed', done: false },
  { id: 'exclusion', label: 'Exclusion work completed', done: false },
  { id: 'cleanup', label: 'Cleanup and sanitation done', done: false },
  { id: 'final_inspection', label: 'Final inspection with customer', done: false },
  { id: 'signature', label: 'Customer signature obtained', done: false },
  { id: 'photos', label: 'Photos taken (before/after)', done: false },
  { id: 'warranty', label: 'Warranty explained', done: false },
  { id: 'followup', label: 'Follow-up scheduled', done: false }
];

// ─── LocalStorage keys ───
export const STORAGE_KEY = 'ww_rockstar';
export const PREF_KEY = 'ww_rockstar_pref';
export const SYNC_URL_KEY = STORAGE_KEY + '_syncUrl';
export const THEME_KEY = 'ww_theme';
export const WEATHER_CACHE_KEY = STORAGE_KEY + '_weather';
