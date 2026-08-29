/**
 * MetricsPage.js — Business analytics page
 * Summary cards, species distribution, status distribution, revenue by tech,
 * jobs by town, monthly trend, average job value, response time
 */

import { SPECIES_ICONS, STATUS_STYLES } from '../constants.js';

function E(s) {
  return String(s || '').replace(
    /[&<>"']/g,
    m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[m]
  );
}

function money(n) {
  return '$' + Math.round(n || 0).toLocaleString();
}

function estimateJob(j) {
  const base =
    {
      Bat: 950,
      Raccoon: 650,
      'Grey Squirrel': 550,
      'Red Squirrel': 575,
      'Flying Squirrel': 750,
      Skunk: 450,
      Groundhog: 450,
      'Carpenter Bee': 350
    }[j.species] || 500;
  return Math.round(base * 1.35);
}

function formatMonth(d) {
  try {
    return new Date(d).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  } catch {
    return 'Unknown';
  }
}

export const MetricsPage = {
  _listeners: [],

  render(state) {
    const jobs = state.jobs || [];
    const visits = state.visits || [];
    const expenses = state.expenses || [];

    if (!jobs.length) {
      return /* html */ `
        <div class="empty-state">
          <div class="empty-icon" aria-hidden="true">📊</div>
          <h4>No data yet</h4>
          <p>Create some jobs to see your business metrics.</p>
        </div>
      `;
    }

    // ─── Summary metrics ───
    const activeJobs = jobs.filter(j => j.status !== 'Closed' && j.status !== 'Cancelled');
    const closedJobs = jobs.filter(j => j.status === 'Closed');
    const totalRevenue = jobs.reduce((a, j) => a + (j.grand_total || j.estimate || estimateJob(j)), 0);
    const avgJobValue = totalRevenue / jobs.length;

    // ─── Species distribution ───
    const speciesCounts = {};
    jobs.forEach(j => {
      speciesCounts[j.species || 'Other'] = (speciesCounts[j.species || 'Other'] || 0) + 1;
    });
    const maxSpeciesCount = Math.max(...Object.values(speciesCounts));

    // ─── Status distribution ───
    const statusCounts = {};
    jobs.forEach(j => {
      statusCounts[j.status || 'Unknown'] = (statusCounts[j.status || 'Unknown'] || 0) + 1;
    });

    // ─── Revenue by tech ───
    const techRevenue = {};
    jobs.forEach(j => {
      const tech = j.assigned_tech || 'Unassigned';
      techRevenue[tech] = (techRevenue[tech] || 0) + (j.grand_total || j.estimate || estimateJob(j));
    });
    const maxTechRevenue = Math.max(...Object.values(techRevenue));

    // ─── Jobs by town ───
    const townCounts = {};
    jobs.forEach(j => {
      townCounts[j.town || 'Unsorted'] = (townCounts[j.town || 'Unsorted'] || 0) + 1;
    });
    const maxTownCount = Math.max(...Object.values(townCounts));

    // ─── Monthly trend ───
    const monthCounts = {};
    jobs.forEach(j => {
      const month = formatMonth(j.created_at || j.created);
      monthCounts[month] = (monthCounts[month] || 0) + 1;
    });
    const sortedMonths = Object.entries(monthCounts)
      .sort((a, b) => new Date(a[0]) - new Date(b[0]))
      .slice(-12);
    const maxMonthCount = Math.max(...Object.values(monthCounts));

    // ─── Response time (days from job creation to first visit) ───
    const responseTimes = [];
    jobs.forEach(j => {
      const jobVisits = visits.filter(v => v.job_id === j.id);
      if (jobVisits.length) {
        const jobDate = new Date(j.created_at || j.created);
        const firstVisit = new Date(jobVisits[0].date || jobVisits[0].created_at);
        const days = Math.round((firstVisit - jobDate) / (1000 * 60 * 60 * 24));
        if (days >= 0) responseTimes.push(days);
      }
    });
    const avgResponse = responseTimes.length
      ? (responseTimes.reduce((a, b) => a + b, 0) / responseTimes.length).toFixed(1)
      : 'N/A';

    // ─── Total expenses ───
    const totalExpenses = expenses.reduce((a, e) => a + (e.amount || 0), 0);
    const netRevenue = totalRevenue - totalExpenses;

    return /* html */ `
      <div class="card stack">
        <h2>📊 Business Metrics</h2>
        <p class="tiny">Analytics for ${jobs.length} jobs across ${Object.keys(townCounts).length} towns</p>
      </div>

      <!-- Summary Cards -->
      <div class="grid">
        <div class="card stat-card">
          <div class="stat-icon" aria-hidden="true">🦝</div>
          <div class="stat-label">Active Jobs</div>
          <div class="stat">${activeJobs.length}</div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon" aria-hidden="true">✅</div>
          <div class="stat-label">Closed Jobs</div>
          <div class="stat">${closedJobs.length}</div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon" aria-hidden="true">💰</div>
          <div class="stat-label">Total Revenue</div>
          <div class="stat">${money(totalRevenue)}</div>
        </div>
        <div class="card stat-card">
          <div class="stat-icon" aria-hidden="true">📈</div>
          <div class="stat-label">Avg Job Value</div>
          <div class="stat">${money(avgJobValue)}</div>
        </div>
      </div>

      <!-- Financial Summary -->
      <div class="card" style="background:var(--card2);">
        <div class="section-title" style="margin-top:0;">Financial Summary</div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;font-size:14px;">
          <div><div class="tiny">Gross Revenue</div><div style="font-weight:600;color:var(--green);">${money(totalRevenue)}</div></div>
          <div><div class="tiny">Total Expenses</div><div style="font-weight:600;color:var(--red);">${money(totalExpenses)}</div></div>
          <div style="grid-column:1/-1;border-top:1px solid var(--border);padding-top:8px;">
            <div class="tiny">Net Revenue</div>
            <div style="font-weight:700;font-size:18px;color:${netRevenue >= 0 ? 'var(--green)' : 'var(--red)'};">${money(netRevenue)}</div>
          </div>
        </div>
      </div>

      <!-- Species Distribution -->
      <div class="section-title">Species Distribution</div>
      <div class="card">
        ${Object.entries(speciesCounts)
          .sort((a, b) => b[1] - a[1])
          .map(([species, count]) => {
            const pct = Math.round((count / jobs.length) * 100);
            const barWidth = Math.round((count / maxSpeciesCount) * 100);
            const icon = SPECIES_ICONS[species] || '🐾';
            return `
            <div style="margin-bottom:10px;">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;">
                <span><span style="font-size:18px;margin-right:4px;">${icon}</span>${E(species)}</span>
                <span style="font-weight:600;font-size:13px;">${count} (${pct}%)</span>
              </div>
              <div class="prog" style="margin-top:0;height:8px;" role="progressbar" aria-label="${E(species)}: ${count} jobs" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100">
                <div class="bar" style="width:${barWidth}%"></div>
              </div>
            </div>
          `;
          })
          .join('')}
      </div>

      <!-- Status Distribution -->
      <div class="section-title">Status Distribution</div>
      <div class="card">
        ${Object.entries(statusCounts)
          .sort((a, b) => b[1] - a[1])
          .map(([status, count]) => {
            const pct = Math.round((count / jobs.length) * 100);
            const sc = STATUS_STYLES[status] || 'active';
            return `
            <div style="display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid var(--border);">
              <span><span class="status-pill ${sc}">${E(status)}</span></span>
              <span style="font-weight:600;">${count} (${pct}%)</span>
            </div>
          `;
          })
          .join('')}
      </div>

      <!-- Revenue by Technician -->
      ${
        Object.keys(techRevenue).length > 1 || Object.keys(techRevenue)[0] !== 'Unassigned'
          ? `<div class="section-title">Revenue by Technician</div>
           <div class="card">
             ${Object.entries(techRevenue)
               .sort((a, b) => b[1] - a[1])
               .map(([tech, rev]) => {
                 const barWidth = Math.round((rev / maxTechRevenue) * 100);
                 return `
                 <div style="margin-bottom:10px;">
                   <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;">
                     <span>${E(tech)}</span>
                     <span style="font-weight:600;">${money(rev)}</span>
                   </div>
                   <div class="prog" style="margin-top:0;height:8px;" role="progressbar" aria-label="${E(tech)} revenue" aria-valuenow="${barWidth}" aria-valuemin="0" aria-valuemax="100">
                     <div class="bar" style="width:${barWidth}%"></div>
                   </div>
                 </div>
               `;
               })
               .join('')}
           </div>`
          : ''
      }

      <!-- Jobs by Town -->
      <div class="section-title">Jobs by Town</div>
      <div class="card">
        ${Object.entries(townCounts)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 15)
          .map(([town, count]) => {
            const barWidth = Math.round((count / maxTownCount) * 100);
            return `
            <div style="margin-bottom:10px;">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px;">
                <span>📍 ${E(town)}</span>
                <span style="font-weight:600;">${count} job${count !== 1 ? 's' : ''}</span>
              </div>
              <div class="prog" style="margin-top:0;height:8px;" role="progressbar" aria-label="${E(town)} jobs" aria-valuenow="${barWidth}" aria-valuemin="0" aria-valuemax="100">
                <div class="bar" style="width:${barWidth}%"></div>
              </div>
            </div>
          `;
          })
          .join('')}
      </div>

      <!-- Monthly Trend -->
      ${
        sortedMonths.length > 1
          ? `<div class="section-title">Monthly Trend</div>
           <div class="card">
             <div style="display:flex;align-items:flex-end;gap:4px;height:120px;padding:10px 0;">
               ${sortedMonths
                 .map(([month, count]) => {
                   const barHeight = Math.round((count / maxMonthCount) * 100);
                   return `
                   <div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:4px;min-width:0;">
                     <span style="font-size:10px;font-weight:600;">${count}</span>
                     <div style="width:100%;background:linear-gradient(to top,var(--accent),var(--accent2));border-radius:4px 4px 0 0;min-height:4px;height:${barHeight}%;opacity:0.8;transition:height 0.5s ease;"></div>
                     <span style="font-size:9px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;width:100%;text-align:center;">${month}</span>
                   </div>
                 `;
                 })
                 .join('')}
             </div>
           </div>`
          : ''
      }

      <!-- Response Time -->
      <div class="section-title">Performance Metrics</div>
      <div class="grid">
        <div class="card stat-card">
          <div class="stat-label">Avg Response Time</div>
          <div class="stat">${avgResponse}d</div>
          <div class="tiny">Days from job creation to first visit</div>
        </div>
        <div class="card stat-card">
          <div class="stat-label">Completion Rate</div>
          <div class="stat">${jobs.length ? Math.round((closedJobs.length / jobs.length) * 100) : 0}%</div>
          <div class="tiny">${closedJobs.length} of ${jobs.length} jobs closed</div>
        </div>
        <div class="card stat-card">
          <div class="stat-label">Total Visits</div>
          <div class="stat">${visits.length}</div>
          <div class="tiny">${jobs.length ? (visits.length / jobs.length).toFixed(1) : 0} visits per job avg</div>
        </div>
        <div class="card stat-card">
          <div class="stat-label">Expense Ratio</div>
          <div class="stat">${totalRevenue ? ((totalExpenses / totalRevenue) * 100).toFixed(1) : 0}%</div>
          <div class="tiny">Expenses vs revenue</div>
        </div>
      </div>
    `;
  },

  afterRender(state) {
    // No interactive elements needed for metrics — it's display-only
  },

  unmount() {
    this._listeners = [];
  }
};
