/**
 * JobList.js — Job listing page
 * Search bar, filters, sort, job cards, pagination, empty state
 */

import {
  SPECIES,
  STATUSES,
  SPECIES_ICONS,
  STATUS_STYLES
} from "../constants.js";

function E(s) {
  return String(s || "").replace(/[&<>"']/g, (m) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[m]));
}

function money(n) {
  return "$" + Math.round(n || 0).toLocaleString();
}

function estimateJob(j) {
  const base = { Bat: 950, Raccoon: 650, "Grey Squirrel": 550, "Red Squirrel": 575, "Flying Squirrel": 750, Skunk: 450, Groundhog: 450, "Carpenter Bee": 350 }[j.species] || 500;
  return Math.round(base * 1.35);
}

/**
 * Calculate job completion score (0-100%) using pre-computed index counts.
 * Performance: Uses O(1) Map/Set lookups instead of scanning full arrays.
 */
function scoreJob(jobId, visitCounts, repairCounts, photoCounts, signatureSet) {
  const hasVisits = (visitCounts.get(jobId) || 0) > 0;
  const hasPhotos = (photoCounts.get(jobId) || 0) > 0;
  const hasRepairs = (repairCounts.get(jobId) || 0) > 0;
  const hasSignatures = signatureSet.has(jobId);
  return Math.min(100, (hasVisits ? 25 : 0) + (hasPhotos ? 25 : 0) + (hasRepairs ? 25 : 0) + (hasSignatures ? 25 : 0));
}

function tel(p) {
  return "tel:" + String(p || "").replace(/[^\d+]/g, "");
}

const PAGE_SIZE = 10;

export const JobList = {
  _listeners: [],
  _page: 1,
  _searchQuery: "",
  _filters: { status: "", species: "", tech: "", town: "" },
  _sort: "newest",

  render(state) {
    const jobs = state.jobs || [];
    const visits = state.visits || [];
    const repairs = state.repairs || [];
    const photos = state.photos || [];
    const signatures = state.signatures || [];

    // Performance Optimization: Build O(1) hash maps for entity counts per job.
    // Replaces O(N * M) repeated linear array scans during card rendering with single linear indexing pass.
    const visitCounts = new Map();
    for (const v of visits) {
      if (v.job_id) visitCounts.set(v.job_id, (visitCounts.get(v.job_id) || 0) + 1);
    }

    const repairCounts = new Map();
    for (const r of repairs) {
      if (r.job_id) repairCounts.set(r.job_id, (repairCounts.get(r.job_id) || 0) + 1);
    }

    const photoCounts = new Map();
    for (const p of photos) {
      if (p.job_id) photoCounts.set(p.job_id, (photoCounts.get(p.job_id) || 0) + 1);
    }

    const signatureSet = new Set();
    for (const s of signatures) {
      if (s.job_id) signatureSet.add(s.job_id);
    }

    // Filter and sort
    let filtered = this._filterJobs(jobs);
    filtered = this._sortJobs(filtered);

    // Pagination
    const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
    const startIdx = 0;
    const endIdx = Math.min(this._page * PAGE_SIZE, filtered.length);
    const pageJobs = filtered.slice(startIdx, endIdx);

    // Unique towns and techs for filter dropdowns
    const towns = [...new Set(jobs.map((j) => j.town).filter(Boolean))].sort();
    const techs = [...new Set(jobs.map((j) => j.assigned_tech).filter(Boolean))].sort();

    return /* html */ `
      <!-- Search -->
      <div class="search-box">
        <div class="search-input-wrap">
          <input
            type="search"
            id="jobSearch"
            placeholder="Search jobs, customers, addresses, species..."
            aria-label="Search jobs"
            value="${E(this._searchQuery)}"
            autocomplete="off"
          />
          ${this._searchQuery ? `<button class="search-clear" id="searchClear" aria-label="Clear search">&times;</button>` : ""}
        </div>
      </div>

      <!-- Filters -->
      <div class="filter-bar">
        <select id="filterStatus" aria-label="Filter by status">
          <option value="">All Statuses</option>
          ${STATUSES.map((s) => `<option value="${E(s)}" ${this._filters.status === s ? "selected" : ""}>${E(s)}</option>`).join("")}
        </select>
        <select id="filterSpecies" aria-label="Filter by species">
          <option value="">All Species</option>
          ${SPECIES.map((s) => `<option value="${E(s)}" ${this._filters.species === s ? "selected" : ""}>${E(s)}</option>`).join("")}
        </select>
        <select id="filterTech" aria-label="Filter by technician">
          <option value="">All Techs</option>
          ${techs.map((t) => `<option value="${E(t)}" ${this._filters.tech === t ? "selected" : ""}>${E(t)}</option>`).join("")}
        </select>
        <select id="filterTown" aria-label="Filter by town">
          <option value="">All Towns</option>
          ${towns.map((t) => `<option value="${E(t)}" ${this._filters.town === t ? "selected" : ""}>${E(t)}</option>`).join("")}
        </select>
      </div>

      <!-- Sort + Count -->
      <div class="sort-bar">
        <span class="results-count" aria-live="polite">${filtered.length} job${filtered.length !== 1 ? "s" : ""}</span>
        <select id="sortJobs" aria-label="Sort jobs">
          <option value="newest" ${this._sort === "newest" ? "selected" : ""}>Newest first</option>
          <option value="oldest" ${this._sort === "oldest" ? "selected" : ""}>Oldest first</option>
          <option value="customer" ${this._sort === "customer" ? "selected" : ""}>Customer A-Z</option>
          <option value="status" ${this._sort === "status" ? "selected" : ""}>Status</option>
        </select>
      </div>

      <!-- Job List -->
      <div id="jobList">
        ${pageJobs.length
          ? pageJobs.map((j) => this._jobCard(j, visitCounts, repairCounts, photoCounts, signatureSet)).join("")
          : `<div class="empty-state">
              <div class="empty-icon" aria-hidden="true">🔍</div>
              <h4>${this._searchQuery || this._filters.status || this._filters.species ? "No matching jobs" : "No jobs yet"}</h4>
              <p>${this._searchQuery || this._filters.status || this._filters.species ? "Try adjusting your search or filters." : "Create your first job to get started."}</p>
             </div>`
        }
      </div>

      <!-- Load More -->
      ${endIdx < filtered.length
        ? `<div class="load-more">
            <button id="loadMoreBtn" aria-label="Load more jobs">Load more (${filtered.length - endIdx} remaining)</button>
           </div>`
        : ""
      }
    `;
  },

  afterRender(state) {
    // Debounced search
    const searchInput = document.getElementById("jobSearch");
    const searchClear = document.getElementById("searchClear");
    const filterStatus = document.getElementById("filterStatus");
    const filterSpecies = document.getElementById("filterSpecies");
    const filterTech = document.getElementById("filterTech");
    const filterTown = document.getElementById("filterTown");
    const sortSelect = document.getElementById("sortJobs");
    const loadMoreBtn = document.getElementById("loadMoreBtn");

    if (searchInput) {
      const debouncedSearch = this._debounce((q) => {
        this._searchQuery = q;
        this._page = 1;
        state.rerender?.();
      }, 250);
      const handler = (e) => debouncedSearch(e.target.value);
      searchInput.addEventListener("input", handler);
      this._listeners.push({ el: searchInput, type: "input", fn: handler });
    }

    if (searchClear) {
      const handler = () => {
        this._searchQuery = "";
        this._page = 1;
        state.rerender?.();
      };
      searchClear.addEventListener("click", handler);
      this._listeners.push({ el: searchClear, type: "click", fn: handler });
    }

    // Filters
    const filterHandler = () => {
      this._filters = {
        status: filterStatus?.value || "",
        species: filterSpecies?.value || "",
        tech: filterTech?.value || "",
        town: filterTown?.value || "",
      };
      this._page = 1;
      state.rerender?.();
    };

    [filterStatus, filterSpecies, filterTech, filterTown].forEach((el) => {
      if (el) {
        el.addEventListener("change", filterHandler);
        this._listeners.push({ el, type: "change", fn: filterHandler });
      }
    });

    // Sort
    if (sortSelect) {
      const handler = () => {
        this._sort = sortSelect.value;
        this._page = 1;
        state.rerender?.();
      };
      sortSelect.addEventListener("change", handler);
      this._listeners.push({ el: sortSelect, type: "change", fn: handler });
    }

    // Load more
    if (loadMoreBtn) {
      const handler = () => {
        this._page++;
        state.rerender?.();
      };
      loadMoreBtn.addEventListener("click", handler);
      this._listeners.push({ el: loadMoreBtn, type: "click", fn: handler });
    }

    // Job card clicks (event delegation)
    const jobList = document.getElementById("jobList");
    if (jobList) {
      const clickHandler = (e) => {
        const card = e.target.closest(".job-card[data-job-id]");
        if (!card) return;
        if (e.target.closest(".job-actions")) return;
        const jobId = card.dataset.jobId;
        if (state.navigate) state.navigate(`jobs/${jobId}`);
      };
      jobList.addEventListener("click", clickHandler);
      this._listeners.push({ el: jobList, type: "click", fn: clickHandler });

      // Navigate button handler
      const actionHandler = (e) => {
        const btn = e.target.closest("[data-navigate]");
        if (!btn) return;
        e.stopPropagation();
        const target = btn.dataset.navigate;
        const lat = btn.dataset.lat;
        const lng = btn.dataset.lng;
        const addr = btn.dataset.address;
        if (target === "navigate") {
          if (lat && lng && lat !== "null" && lat !== "undefined") {
            window.open(`https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&travelmode=driving`, "_blank");
          } else if (addr) {
            window.open(`https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(addr)}&travelmode=driving`, "_blank");
          }
        }
      };
      jobList.addEventListener("click", actionHandler);
      this._listeners.push({ el: jobList, type: "click", fn: actionHandler });
    }
  },

  unmount() {
    this._listeners.forEach(({ el, type, fn }) => el.removeEventListener(type, fn));
    this._listeners = [];
    this._page = 1;
    this._searchQuery = "";
    this._filters = { status: "", species: "", tech: "", town: "" };
    this._sort = "newest";
  },

  // ─── Internal helpers ───
  _filterJobs(jobs) {
    const q = this._searchQuery.toLowerCase().trim();
    return jobs.filter((j) => {
      const matchesSearch = !q ||
        (j.title || "").toLowerCase().includes(q) ||
        (j.customer || "").toLowerCase().includes(q) ||
        (j.address || "").toLowerCase().includes(q) ||
        (j.town || "").toLowerCase().includes(q) ||
        (j.species || "").toLowerCase().includes(q) ||
        (j.scope || "").toLowerCase().includes(q) ||
        (j.status || "").toLowerCase().includes(q);
      const matchesStatus = !this._filters.status || j.status === this._filters.status;
      const matchesSpecies = !this._filters.species || j.species === this._filters.species;
      const matchesTech = !this._filters.tech || j.assigned_tech === this._filters.tech;
      const matchesTown = !this._filters.town || j.town === this._filters.town;
      return matchesSearch && matchesStatus && matchesSpecies && matchesTech && matchesTown;
    });
  },

  _sortJobs(jobs) {
    const sorted = [...jobs];
    switch (this._sort) {
      case "oldest":
        sorted.sort((a, b) => new Date(a.created_at || a.created || 0) - new Date(b.created_at || b.created || 0));
        break;
      case "customer":
        sorted.sort((a, b) => (a.customer || "").localeCompare(b.customer || ""));
        break;
      case "status":
        sorted.sort((a, b) => (a.status || "").localeCompare(b.status || ""));
        break;
      case "newest":
      default:
        sorted.sort((a, b) => new Date(b.created_at || b.created || 0) - new Date(a.created_at || a.created || 0));
    }
    return sorted;
  },

  _jobCard(j, visitCounts, repairCounts, photoCounts, signatureSet) {
    const icon = SPECIES_ICONS[j.species] || "🐾";
    const sc = STATUS_STYLES[j.status] || "active";
    const v = visitCounts.get(j.id) || 0;
    const r = repairCounts.get(j.id) || 0;
    const p = photoCounts.get(j.id) || 0;
    const s = scoreJob(j.id, visitCounts, repairCounts, photoCounts, signatureSet);
    const est = estimateJob(j);

    return /* html */ `
      <div class="card stack job-card" data-job-id="${j.id}">
        <div class="job-header">
          <span class="species-icon" aria-hidden="true">${icon}</span>
          <h3>${E(j.title || j.species + " job")}</h3>
          <span class="status-pill ${sc}">${E(j.status)}</span>
        </div>
        <div class="tiny">${E(j.customer)} &middot; <a href="${tel(j.phone)}">${E(j.phone)}</a></div>
        <div class="tiny">${E(j.address)}${j.town ? ", " + E(j.town) : ""}</div>
        <div style="margin-top:6px;">
          <span class="pill">${E(j.species)}</span>
          <span class="pill">${v} visits</span>
          <span class="pill">${r} repairs</span>
          <span class="pill">${p} photos</span>
          ${j.latitude ? '<span class="pill info">📍 GPS</span>' : ""}
        </div>
        <div class="prog" role="progressbar" aria-label="Job completion ${s}%" aria-valuenow="${s}" aria-valuemin="0" aria-valuemax="100">
          <div class="bar" style="width:${s}%"></div>
        </div>
        <div class="tiny">Score ${s}% &middot; Est ${money(est)}</div>
        <div class="job-actions">
          <button class="primary" data-navigate="job" data-job-id="${j.id}">Open</button>
          <button class="secondary" data-navigate="navigate" data-lat="${j.latitude || ""}" data-lng="${j.longitude || ""}" data-address="${E(j.address)}">Navigate</button>
        </div>
      </div>
    `;
  },

  _debounce(fn, ms) {
    let t;
    return (...args) => {
      clearTimeout(t);
      t = setTimeout(() => fn(...args), ms);
    };
  },
};
