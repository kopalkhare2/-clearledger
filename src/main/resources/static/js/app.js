/**
 * ClearLedger Operations Portal — Main Application Engine
 * Pure relative API integration with Spring Boot Core Ledger
 */

// Application State & Local Session Cache
const AppState = {
  activeScreen: 'dashboard',
  cachedAccounts: JSON.parse(localStorage.getItem('cl_accounts') || '[]'),
  cachedBatches: JSON.parse(localStorage.getItem('cl_batches') || '[]'),
  discrepancies: [],
  selectedMatchId: null,
};

function saveCachedAccounts() {
  localStorage.setItem('cl_accounts', JSON.stringify(AppState.cachedAccounts));
}

function saveCachedBatches() {
  localStorage.setItem('cl_batches', JSON.stringify(AppState.cachedBatches));
}

function addCachedAccount(account) {
  if (!account || !account.id) return;
  AppState.cachedAccounts = AppState.cachedAccounts.filter(a => a.id !== account.id);
  AppState.cachedAccounts.unshift(account);
  if (AppState.cachedAccounts.length > 50) AppState.cachedAccounts.pop();
  saveCachedAccounts();
}

function addCachedBatch(batch) {
  if (!batch || !batch.id) return;
  AppState.cachedBatches = AppState.cachedBatches.filter(b => b.id !== batch.id);
  AppState.cachedBatches.unshift(batch);
  if (AppState.cachedBatches.length > 50) AppState.cachedBatches.pop();
  saveCachedBatches();
}

// --- Formatters & Helpers ---
function formatMoney(amount, currency = 'INR') {
  if (amount === undefined || amount === null) return '0.00 ' + currency;
  const num = typeof amount === 'number' ? amount : parseFloat(amount);
  return `${num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

function getStatusBadge(status) {
  const map = {
    MATCHED: 'badge-success',
    AMOUNT_MISMATCH: 'badge-danger',
    FEE_DISCREPANCY: 'badge-warning',
    UNMATCHED_INTERNAL: 'badge-purple',
    UNMATCHED_EXTERNAL: 'badge-purple',
    RESOLVED: 'badge-success',
    DISPUTED: 'badge-danger',
    COMPLETED: 'badge-success',
    PENDING: 'badge-warning',
    FAILED: 'badge-danger',
    RECONCILED: 'badge-success',
    ACTIVE: 'badge-success',
    FROZEN: 'badge-warning',
    CLOSED: 'badge-danger',
  };
  const cls = map[status] || 'badge-muted';
  return `<span class="badge ${cls}">${status}</span>`;
}

// --- Toast Notification System ---
function showToast(message, type = 'info', title = '') {
  const container = document.getElementById('toastContainer');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  
  const iconMap = {
    success: '✓',
    error: '✕',
    warning: '⚠',
    info: 'ℹ',
  };

  toast.innerHTML = `
    <div style="font-weight:700; font-size:1rem; opacity:0.9;">${iconMap[type] || 'ℹ'}</div>
    <div style="flex:1;">
      ${title ? `<div style="font-weight:600; font-size:0.8rem; margin-bottom:0.15rem; color:var(--text-primary);">${title}</div>` : ''}
      <div style="font-size:0.775rem; color:var(--text-secondary);">${message}</div>
    </div>
    <button style="color:var(--text-muted); font-size:1.1rem; line-height:1;" onclick="this.parentElement.remove()">×</button>
  `;

  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(12px)';
    toast.style.transition = 'all 0.2s ease';
    setTimeout(() => toast.remove(), 250);
  }, 4000);
}

// --- Modal Dialog System ---
function openModal(title, bodyHtml, onConfirm = null, confirmText = 'Confirm') {
  const backdrop = document.getElementById('modalBackdrop');
  const modalTitle = document.getElementById('modalTitle');
  const modalBody = document.getElementById('modalBody');
  const modalConfirmBtn = document.getElementById('modalConfirmBtn');

  modalTitle.textContent = title;
  modalBody.innerHTML = bodyHtml;

  if (onConfirm) {
    modalConfirmBtn.style.display = 'inline-flex';
    modalConfirmBtn.textContent = confirmText;
    modalConfirmBtn.onclick = async () => {
      try {
        await onConfirm();
        closeModal();
      } catch (err) {
        showToast(err.message, 'error', 'Action Error');
      }
    };
  } else {
    modalConfirmBtn.style.display = 'none';
  }

  backdrop.classList.add('active');
}

function closeModal() {
  const backdrop = document.getElementById('modalBackdrop');
  backdrop.classList.remove('active');
}

// --- Global Ledger Health Status ---
async function refreshLedgerHealth() {
  const pill = document.getElementById('ledgerHealthPill');
  if (!pill) return;

  try {
    const report = await window.api.getTrialBalance();
    if (report.isBalanced) {
      pill.innerHTML = `<span class="status-dot"></span> Invariants Balanced (${formatMoney(report.totalDebits)})`;
      pill.className = 'ledger-health-pill';
      pill.style.backgroundColor = 'var(--color-success-bg)';
      pill.style.color = 'var(--color-success)';
      pill.style.borderColor = 'var(--color-success-border)';
    } else {
      pill.innerHTML = `<span class="status-dot" style="background:var(--color-danger)"></span> IMBALANCED (Diff: ${Math.abs(report.totalDebits - report.totalCredits)})`;
      pill.className = 'ledger-health-pill';
      pill.style.backgroundColor = 'var(--color-danger-bg)';
      pill.style.color = 'var(--color-danger)';
      pill.style.borderColor = 'var(--color-danger-border)';
    }
  } catch (err) {
    pill.innerHTML = `<span class="status-dot"></span> Ledger Online`;
  }
}

// --- Router / Navigation Switcher ---
function navigateTo(screenId) {
  AppState.activeScreen = screenId;
  window.location.hash = screenId;

  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('active', el.dataset.screen === screenId);
  });

  const metadataMap = {
    dashboard: { title: 'Dashboard', subtitle: 'Executive overview & ledger integrity' },
    accounts: { title: 'Accounts', subtitle: 'Account directory, live balances & ledgers' },
    transactions: { title: 'Transfers', subtitle: 'Atomic double-entry fund movements' },
    statements: { title: 'Account Statements', subtitle: 'Historical reconstructed ledger balances' },
    reconciliation: { title: 'Settlement Feeds', subtitle: 'Batch ingestion & 2-way matching engine' },
    discrepancies: { title: 'Discrepancy Desk', subtitle: 'Reconciliation exception management workbench' },
    resolution_history: { title: 'Audit Timeline', subtitle: 'Permanent append-only operational audit log' },
    'trial-balance': { title: 'Trial Balance', subtitle: 'System-wide invariant audit & double-entry validation' },
  };

  const meta = metadataMap[screenId] || { title: 'ClearLedger Portal', subtitle: 'Operations Console' };
  document.getElementById('pageTitle').textContent = meta.title;
  const subEl = document.getElementById('pageSubtitle');
  if (subEl) subEl.textContent = meta.subtitle;

  const contentArea = document.getElementById('contentArea');
  contentArea.innerHTML = '<div class="empty-state"><div class="spinner"></div><p style="margin-top:0.75rem;">Loading screen...</p></div>';

  switch (screenId) {
    case 'dashboard':
      renderDashboard(contentArea);
      break;
    case 'accounts':
      renderAccounts(contentArea);
      break;
    case 'transactions':
      renderTransactions(contentArea);
      break;
    case 'reconciliation':
      renderReconciliation(contentArea);
      break;
    case 'discrepancies':
      renderDiscrepancies(contentArea);
      break;
    case 'resolution_history':
      renderResolutionHistory(contentArea);
      break;
    case 'trial-balance':
      renderTrialBalance(contentArea);
      break;
    case 'statements':
      renderStatements(contentArea);
      break;
    default:
      renderDashboard(contentArea);
  }

  refreshLedgerHealth();
}

// ============================================================
// 1. DASHBOARD VIEW
// ============================================================
async function renderDashboard(container) {
  try {
    const [trialBalance, discrepanciesPage] = await Promise.all([
      window.api.getTrialBalance().catch(() => null),
      window.api.getDiscrepancies(0, 5).catch(() => ({ content: [] })),
    ]);

    const totalDebits = trialBalance ? trialBalance.totalDebits : 0;
    const totalCredits = trialBalance ? trialBalance.totalCredits : 0;
    const accountCount = trialBalance ? trialBalance.accountCount : AppState.cachedAccounts.length;
    const entryCount = trialBalance ? trialBalance.entryCount : 0;
    const isBalanced = trialBalance ? trialBalance.isBalanced : true;
    const openDiscrepancies = discrepanciesPage?.content?.filter(d => d.status !== 'RESOLVED') || [];

    const badge = document.getElementById('discrepanciesBadge');
    if (badge) {
      badge.textContent = openDiscrepancies.length || '';
      badge.style.display = openDiscrepancies.length ? 'inline-block' : 'none';
    }

    container.innerHTML = `
      <!-- Quick Action Bar -->
      <div class="action-bar">
        <button class="btn btn-primary" onclick="openCreateAccountModal()">+ Open Account</button>
        <button class="btn btn-secondary" onclick="openTransferModal()">New Transfer</button>
        <button class="btn btn-secondary" onclick="openIngestBatchModal()">Ingest Feed</button>
        <button class="btn btn-secondary" onclick="navigateTo('trial-balance')">Audit Invariants</button>
      </div>

      <!-- Financial Metrics KPI Grid -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-label">Ledger Invariant Status</div>
          <div class="kpi-value" style="color: ${isBalanced ? 'var(--color-success)' : 'var(--color-danger)'};">
            ${isBalanced ? 'BALANCED' : 'IMBALANCED'}
          </div>
          <div class="kpi-subtext">Invariant: ΣDebits == ΣCredits</div>
        </div>

        <div class="kpi-card">
          <div class="kpi-label">Total System Debits</div>
          <div class="kpi-value font-mono">${formatMoney(totalDebits)}</div>
          <div class="kpi-subtext">${entryCount} recorded ledger entries</div>
        </div>

        <div class="kpi-card">
          <div class="kpi-label">Total System Credits</div>
          <div class="kpi-value font-mono">${formatMoney(totalCredits)}</div>
          <div class="kpi-subtext">Offsetting balanced credit legs</div>
        </div>

        <div class="kpi-card">
          <div class="kpi-label">Active Accounts</div>
          <div class="kpi-value font-mono">${accountCount}</div>
          <div class="kpi-subtext">Standard double-entry accounts</div>
        </div>

        <div class="kpi-card">
          <div class="kpi-label">Open Discrepancies</div>
          <div class="kpi-value font-mono" style="color: ${openDiscrepancies.length > 0 ? 'var(--color-warning)' : 'var(--color-success)'}">
            ${openDiscrepancies.length}
          </div>
          <div class="kpi-subtext">Reconciliation exceptions flagged</div>
        </div>
      </div>

      <!-- Open Discrepancies Exceptions Panel -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">Reconciliation Exceptions</h2>
            <div class="panel-subtitle">Matches requiring operational review or adjustment</div>
          </div>
          <button class="btn btn-secondary btn-sm" onclick="navigateTo('discrepancies')">Open Workbench →</button>
        </div>
        ${openDiscrepancies.length === 0 ? `
          <div class="empty-state">
            <p>✓ All settlement records are balanced. Zero pending discrepancies.</p>
          </div>
        ` : `
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Match ID</th>
                  <th>Status</th>
                  <th>Internal Tx</th>
                  <th>External Tx</th>
                  <th class="text-right">Internal Gross</th>
                  <th class="text-right">External Net</th>
                  <th class="text-center">Action</th>
                </tr>
              </thead>
              <tbody>
                ${openDiscrepancies.map(d => `
                  <tr>
                    <td class="font-mono">#${d.id}</td>
                    <td>${getStatusBadge(d.status)}</td>
                    <td class="font-mono">${d.internalTxReference || '—'}</td>
                    <td class="font-mono">${d.externalTxId || '—'}</td>
                    <td class="text-right font-mono">${d.internalAmount !== null ? formatMoney(d.internalAmount) : '—'}</td>
                    <td class="text-right font-mono">${d.externalNetAmount !== null ? formatMoney(d.externalNetAmount) : '—'}</td>
                    <td class="text-center">
                      <button class="btn btn-primary btn-sm" onclick="openResolveModal(${d.id}, '${d.status}')">
                        Resolve
                      </button>
                    </td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        `}
      </div>

      <!-- Recent Accounts Session Table -->
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">Session Accounts</h2>
            <div class="panel-subtitle">Quick access to active accounts in this session</div>
          </div>
          <button class="btn btn-secondary btn-sm" onclick="navigateTo('accounts')">Manage Accounts →</button>
        </div>
        ${AppState.cachedAccounts.length === 0 ? `
          <div class="empty-state">
            <p>No accounts in local session. Click "+ Open Account" or use the Accounts directory.</p>
          </div>
        ` : `
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Account Number</th>
                  <th>User Profile</th>
                  <th>Currency</th>
                  <th class="text-right">Current Balance</th>
                  <th class="text-center">Actions</th>
                </tr>
              </thead>
              <tbody>
                ${AppState.cachedAccounts.slice(0, 6).map(acc => `
                  <tr>
                    <td class="font-mono">#${acc.id}</td>
                    <td class="font-mono" style="font-weight:600;">${acc.accountNumber}</td>
                    <td>${acc.userId ? `User #${acc.userId}` : '—'}</td>
                    <td><span class="badge badge-muted">${acc.currency}</span></td>
                    <td class="text-right font-mono" style="font-weight:600;">${formatMoney(acc.balance, acc.currency)}</td>
                    <td class="text-center">
                      <button class="btn btn-secondary btn-sm" onclick="viewAccountTransactions(${acc.id})">Ledger</button>
                      <button class="btn btn-secondary btn-sm" onclick="viewAccountStatement(${acc.id})">Statement</button>
                    </td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        `}
      </div>
    `;
  } catch (err) {
    container.innerHTML = `<div class="empty-state" style="color:var(--color-danger);">Failed to load dashboard: ${err.message}</div>`;
  }
}

// ============================================================
// 2. ACCOUNTS VIEW
// ============================================================
async function renderAccounts(container) {
  container.innerHTML = `
    <div class="action-bar">
      <button class="btn btn-primary" onclick="openCreateAccountModal()">+ Open Account</button>
      <div style="display:flex; gap:0.5rem; max-width:320px; flex:1;">
        <input type="number" id="accountLookupId" placeholder="Lookup Account ID..." />
        <button class="btn btn-secondary" onclick="lookupAccountById()">Lookup</button>
      </div>
    </div>

    <!-- Active Accounts Table -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Accounts Directory</h2>
          <div class="panel-subtitle">${AppState.cachedAccounts.length} session accounts available</div>
        </div>
      </div>

      <div id="accountsTableContainer">
        ${AppState.cachedAccounts.length === 0 ? `
          <div class="empty-state">
            <p>No accounts loaded in this session. Create a new account or look up by Account ID.</p>
          </div>
        ` : `
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Account Number</th>
                  <th>Currency</th>
                  <th class="text-right">Live Balance</th>
                  <th>Created At</th>
                  <th class="text-center">Actions</th>
                </tr>
              </thead>
              <tbody>
                ${AppState.cachedAccounts.map(acc => `
                  <tr>
                    <td class="font-mono">#${acc.id}</td>
                    <td class="font-mono" style="font-weight:600;">${acc.accountNumber}</td>
                    <td><span class="badge badge-muted">${acc.currency}</span></td>
                    <td class="text-right font-mono" style="font-weight:700;" id="balance-cell-${acc.id}">
                      ${formatMoney(acc.balance, acc.currency)}
                    </td>
                    <td>${formatDate(acc.createdAt)}</td>
                    <td class="text-center">
                      <div style="display:inline-flex; gap:0.35rem;">
                        <button class="btn btn-secondary btn-sm" title="Refresh Live Balance" onclick="refreshAccountBalance(${acc.id})">⟳</button>
                        <button class="btn btn-secondary btn-sm" onclick="viewAccountTransactions(${acc.id})">Ledger</button>
                        <button class="btn btn-secondary btn-sm" onclick="viewAccountStatement(${acc.id})">Statement</button>
                        <button class="btn btn-primary btn-sm" onclick="openTransferModal(${acc.id})">Transfer</button>
                      </div>
                    </td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        `}
      </div>
    </div>

    <!-- Account Details / Transactions Drawer -->
    <div id="accountDetailContainer"></div>
  `;
}

async function lookupAccountById() {
  const input = document.getElementById('accountLookupId');
  const id = input.value.trim();
  if (!id) {
    showToast('Please enter an account ID', 'warning');
    return;
  }
  try {
    const acc = await window.api.getAccount(id);
    addCachedAccount(acc);
    showToast(`Found account ${acc.accountNumber}`, 'success');
    navigateTo('accounts');
  } catch (err) {
    showToast(err.message, 'error', 'Lookup Failed');
  }
}

async function refreshAccountBalance(accountId) {
  try {
    const balanceRes = await window.api.getAccountBalance(accountId);
    const cell = document.getElementById(`balance-cell-${accountId}`);
    if (cell) {
      cell.textContent = formatMoney(balanceRes.balance, balanceRes.currency);
    }
    const cached = AppState.cachedAccounts.find(a => a.id === accountId);
    if (cached) {
      cached.balance = balanceRes.balance;
      saveCachedAccounts();
    }
    showToast(`Balance updated: ${formatMoney(balanceRes.balance, balanceRes.currency)}`, 'info');
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function viewAccountTransactions(accountId) {
  const detailContainer = document.getElementById('accountDetailContainer');
  if (!detailContainer) return;

  detailContainer.innerHTML = '<div class="panel"><div class="spinner"></div> Loading transactions...</div>';

  try {
    const [acc, txnsPage] = await Promise.all([
      window.api.getAccount(accountId),
      window.api.getAccountTransactions(accountId, 0, 30),
    ]);

    const txns = txnsPage?.content || [];

    detailContainer.innerHTML = `
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">Transaction Ledger — Account #${acc.id} (${acc.accountNumber})</h2>
            <div class="panel-subtitle">Current Balance: <strong style="color:var(--text-primary); font-family:var(--font-mono);">${formatMoney(acc.balance, acc.currency)}</strong></div>
          </div>
          <button class="btn btn-secondary btn-sm" onclick="document.getElementById('accountDetailContainer').innerHTML=''">Close</button>
        </div>

        ${txns.length === 0 ? `
          <div class="empty-state"><p>No transactions found for this account.</p></div>
        ` : `
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Tx ID</th>
                  <th>Reference</th>
                  <th>Direction</th>
                  <th>Counterparty</th>
                  <th class="text-right">Amount</th>
                  <th>Status</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                ${txns.map(tx => {
                  const isDebit = tx.sourceAccountId === acc.id;
                  const directionBadge = isDebit 
                    ? `<span class="badge badge-danger">OUTGOING</span>` 
                    : `<span class="badge badge-success">INCOMING</span>`;
                  const counterparty = isDebit ? `Account #${tx.destinationAccountId}` : `Account #${tx.sourceAccountId}`;
                  return `
                    <tr>
                      <td class="font-mono">#${tx.id}</td>
                      <td class="font-mono">${tx.transactionReference}</td>
                      <td>${directionBadge}</td>
                      <td class="font-mono">${counterparty}</td>
                      <td class="text-right font-mono" style="font-weight:600; color:${isDebit ? 'var(--color-danger)' : 'var(--color-success)'};">
                        ${isDebit ? '-' : '+'}${formatMoney(tx.amount, tx.currency)}
                      </td>
                      <td>${getStatusBadge(tx.status)}</td>
                      <td>${formatDate(tx.createdAt)}</td>
                    </tr>
                  `;
                }).join('')}
              </tbody>
            </table>
          </div>
        `}
      </div>
    `;
    detailContainer.scrollIntoView({ behavior: 'smooth' });
  } catch (err) {
    detailContainer.innerHTML = `<div class="panel" style="color:var(--color-danger);">Failed to load history: ${err.message}</div>`;
  }
}

// ============================================================
// 3. TRANSACTIONS VIEW
// ============================================================
async function renderTransactions(container) {
  container.innerHTML = `
    <!-- Transfer Form Panel -->
    <div class="panel" style="max-width:760px; margin:0 auto 1.5rem auto;">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Execute Double-Entry Transfer</h2>
          <div class="panel-subtitle">Guarantees atomic execution and ledger balance invariant</div>
        </div>
      </div>
      <form id="inlineTransferForm" onsubmit="handleInlineTransfer(event)">
        <div style="display:grid; grid-template-columns:1fr 1fr; gap:1rem; margin-bottom:1rem;">
          <div class="form-group">
            <label class="form-label">Source Account ID *</label>
            <input type="number" id="txSourceId" placeholder="e.g. 1" required />
            <div class="form-help">Funds will be debited (-)</div>
          </div>
          <div class="form-group">
            <label class="form-label">Destination Account ID *</label>
            <input type="number" id="txDestId" placeholder="e.g. 2" required />
            <div class="form-help">Funds will be credited (+)</div>
          </div>
        </div>

        <div style="display:grid; grid-template-columns:2fr 1fr; gap:1rem; margin-bottom:1rem;">
          <div class="form-group">
            <label class="form-label">Transfer Amount *</label>
            <input type="number" step="0.01" min="0.01" id="txAmount" placeholder="100.00" class="font-mono" style="font-size:1.1rem; font-weight:600;" required />
          </div>
          <div class="form-group">
            <label class="form-label">Currency *</label>
            <select id="txCurrency">
              <option value="INR" selected>INR</option>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Idempotency Key (UUID)</label>
          <div style="display:flex; gap:0.5rem;">
            <input type="text" id="txIdempotencyKey" class="font-mono" value="${generateUUID()}" required />
            <button type="button" class="btn btn-secondary btn-sm" onclick="document.getElementById('txIdempotencyKey').value = generateUUID()">↻ New</button>
          </div>
          <div class="form-help">Ensures exactly-once execution semantics against network retries.</div>
        </div>

        <div style="display:flex; justify-content:flex-end; margin-top:1.5rem;">
          <button type="submit" class="btn btn-primary">Execute Atomic Transfer</button>
        </div>
      </form>
    </div>

    <!-- Last Transfer Result View -->
    <div id="lastTransferResult" style="max-width:760px; margin:0 auto;"></div>
  `;
}

async function handleInlineTransfer(event) {
  event.preventDefault();
  const sourceId = parseInt(document.getElementById('txSourceId').value, 10);
  const destId = parseInt(document.getElementById('txDestId').value, 10);
  const amount = parseFloat(document.getElementById('txAmount').value);
  const currency = document.getElementById('txCurrency').value;
  const idempotencyKey = document.getElementById('txIdempotencyKey').value.trim();

  if (sourceId === destId) {
    showToast('Source and Destination account cannot be the same', 'error');
    return;
  }

  try {
    const res = await window.api.transfer({
      sourceAccountId: sourceId,
      destinationAccountId: destId,
      amount,
      currency,
      idempotencyKey,
    });

    showToast(`Transfer completed: ${res.transactionReference}`, 'success', 'Transfer Success');

    const resultDiv = document.getElementById('lastTransferResult');
    resultDiv.innerHTML = `
      <div class="panel" style="border-color:var(--color-success-border); background-color:var(--bg-surface-elevated);">
        <div class="panel-header">
          <h2 class="panel-title" style="color:var(--color-success);">✓ Transfer Executed Successfully</h2>
          <span class="badge badge-success">${res.status}</span>
        </div>
        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(180px, 1fr)); gap:1rem; font-size:0.85rem;">
          <div><span class="text-muted">Transaction ID:</span> <strong class="font-mono">#${res.id}</strong></div>
          <div><span class="text-muted">Reference:</span> <strong class="font-mono">${res.transactionReference}</strong></div>
          <div><span class="text-muted">Source Account:</span> <strong class="font-mono">#${res.sourceAccountId}</strong></div>
          <div><span class="text-muted">Destination Account:</span> <strong class="font-mono">#${res.destinationAccountId}</strong></div>
          <div><span class="text-muted">Amount:</span> <strong class="font-mono" style="color:var(--color-success); font-size:1.05rem;">${formatMoney(res.amount, res.currency)}</strong></div>
        </div>
      </div>
    `;

    refreshAccountBalance(sourceId);
    refreshAccountBalance(destId);
    refreshLedgerHealth();
    document.getElementById('txIdempotencyKey').value = generateUUID();
  } catch (err) {
    showToast(err.message, 'error', 'Transfer Failed');
  }
}

// ============================================================
// 4. RECONCILIATION VIEW
// ============================================================
async function renderReconciliation(container) {
  container.innerHTML = `
    <div class="action-bar">
      <button class="btn btn-primary" onclick="openIngestBatchModal()">+ Ingest Batch</button>
      <div style="display:flex; gap:0.5rem; max-width:320px; flex:1;">
        <input type="number" id="reconBatchLookupId" placeholder="Lookup Batch ID..." />
        <button class="btn btn-secondary" onclick="lookupBatchSummary()">View</button>
      </div>
    </div>

    <!-- Cached Batches Feed -->
    <div class="panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Settlement Feeds</h2>
          <div class="panel-subtitle">${AppState.cachedBatches.length} batches recorded in session</div>
        </div>
      </div>

      ${AppState.cachedBatches.length === 0 ? `
        <div class="empty-state">
          <p>No settlement batches in session. Click "+ Ingest Batch" to upload processor feeds.</p>
        </div>
      ` : `
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Batch ID</th>
                <th>Batch Reference</th>
                <th>Provider</th>
                <th>Records</th>
                <th class="text-right">Total Amount</th>
                <th>Status</th>
                <th>Reconciled At</th>
                <th class="text-center">Actions</th>
              </tr>
            </thead>
            <tbody>
              ${AppState.cachedBatches.map(b => `
                <tr>
                  <td class="font-mono">#${b.id}</td>
                  <td class="font-mono" style="font-weight:600;">${b.batchReference}</td>
                  <td><span class="badge badge-info">${b.sourceProvider}</span></td>
                  <td>${b.totalRecords}</td>
                  <td class="text-right font-mono">${formatMoney(b.totalAmount)}</td>
                  <td>${getStatusBadge(b.status)}</td>
                  <td>${formatDate(b.reconciledAt)}</td>
                  <td class="text-center">
                    <div style="display:inline-flex; gap:0.35rem;">
                      ${b.status !== 'RECONCILED' ? `
                        <button class="btn btn-primary btn-sm" onclick="triggerReconcile(${b.id})">Reconcile</button>
                      ` : ''}
                      <button class="btn btn-secondary btn-sm" onclick="loadBatchSummary(${b.id})">Breakdown</button>
                    </div>
                  </td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `}
    </div>

    <!-- Batch Summary Detail Container -->
    <div id="batchSummaryContainer"></div>
  `;
}

async function triggerReconcile(batchId) {
  try {
    showToast(`Running deterministic 2-way matching for Batch #${batchId}...`, 'info');
    const summary = await window.api.reconcileBatch(batchId);
    showToast(`Reconciliation completed: ${summary.totalProcessed} records processed`, 'success');
    
    const b = AppState.cachedBatches.find(x => x.id === batchId);
    if (b) {
      b.status = 'RECONCILED';
      b.reconciledAt = summary.reconciledAt;
      saveCachedBatches();
    }
    
    renderBatchSummaryDetails(summary);
  } catch (err) {
    showToast(err.message, 'error', 'Reconciliation Failed');
  }
}

async function lookupBatchSummary() {
  const id = document.getElementById('reconBatchLookupId').value.trim();
  if (!id) return;
  loadBatchSummary(parseInt(id, 10));
}

async function loadBatchSummary(batchId) {
  const container = document.getElementById('batchSummaryContainer');
  if (!container) return;

  container.innerHTML = '<div class="panel"><div class="spinner"></div> Fetching batch breakdown...</div>';

  try {
    const summary = await window.api.getBatchSummary(batchId);
    renderBatchSummaryDetails(summary);
  } catch (err) {
    container.innerHTML = `<div class="panel" style="color:var(--color-danger);">Failed to load batch summary: ${err.message}</div>`;
  }
}

function renderBatchSummaryDetails(summary) {
  const container = document.getElementById('batchSummaryContainer');
  if (!container) return;

  container.innerHTML = `
    <div class="panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Batch #${summary.batchId} Breakdown (${summary.batchReference})</h2>
          <div class="panel-subtitle">Status: ${getStatusBadge(summary.batchStatus)} | ${summary.totalProcessed} records processed</div>
        </div>
        <button class="btn btn-secondary btn-sm" onclick="document.getElementById('batchSummaryContainer').innerHTML=''">Close</button>
      </div>

      <!-- Match Breakdown Tiles -->
      <div class="kpi-grid" style="margin-bottom:1.5rem;">
        <div class="kpi-card">
          <div class="kpi-label">Matched (Exact)</div>
          <div class="kpi-value font-mono" style="color:var(--color-success); font-size:1.35rem;">${summary.matchedCount}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Amount Mismatch</div>
          <div class="kpi-value font-mono" style="color:var(--color-danger); font-size:1.35rem;">${summary.amountMismatchCount}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Fee Discrepancy</div>
          <div class="kpi-value font-mono" style="color:var(--color-warning); font-size:1.35rem;">${summary.feeDiscrepancyCount}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Unmatched Internal</div>
          <div class="kpi-value font-mono" style="color:var(--color-purple); font-size:1.35rem;">${summary.unmatchedInternalCount}</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Unmatched External</div>
          <div class="kpi-value font-mono" style="color:var(--color-purple); font-size:1.35rem;">${summary.unmatchedExternalCount}</div>
        </div>
      </div>

      <!-- Financial Totals -->
      <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(180px, 1fr)); gap:1rem; margin-bottom:1.5rem; font-size:0.85rem; padding:0.85rem; background-color:var(--bg-app); border-radius:var(--radius-sm); border:1px solid var(--border-subtle);">
        <div><span class="text-muted">Total Internal Amount:</span> <strong class="font-mono">${formatMoney(summary.totalInternalAmount)}</strong></div>
        <div><span class="text-muted">Settled Gross:</span> <strong class="font-mono">${formatMoney(summary.totalSettledGrossAmount)}</strong></div>
        <div><span class="text-muted">Total Fees:</span> <strong class="font-mono">${formatMoney(summary.totalFees)}</strong></div>
        <div><span class="text-muted">Settled Net:</span> <strong class="font-mono">${formatMoney(summary.totalSettledNetAmount)}</strong></div>
      </div>

      <!-- Match Records Table -->
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>Match ID</th>
              <th>Status</th>
              <th>Type</th>
              <th>Internal Tx Ref</th>
              <th>External Tx ID</th>
              <th class="text-right">Internal Gross</th>
              <th class="text-right">Ext Gross</th>
              <th class="text-right">Fee</th>
              <th class="text-right">Ext Net</th>
              <th class="text-center">Action</th>
            </tr>
          </thead>
          <tbody>
            ${summary.matches.map(m => `
              <tr>
                <td class="font-mono">#${m.id}</td>
                <td>${getStatusBadge(m.status)}</td>
                <td><span class="badge badge-muted">${m.matchType}</span></td>
                <td class="font-mono">${m.internalTxReference || '—'}</td>
                <td class="font-mono">${m.externalTxId || '—'}</td>
                <td class="text-right font-mono">${m.internalAmount !== null ? formatMoney(m.internalAmount) : '—'}</td>
                <td class="text-right font-mono">${m.externalGrossAmount !== null ? formatMoney(m.externalGrossAmount) : '—'}</td>
                <td class="text-right font-mono">${m.externalFee !== null ? formatMoney(m.externalFee) : '—'}</td>
                <td class="text-right font-mono">${m.externalNetAmount !== null ? formatMoney(m.externalNetAmount) : '—'}</td>
                <td class="text-center">
                  <div style="display:inline-flex; gap:0.3rem;">
                    ${m.status !== 'MATCHED' && m.status !== 'RESOLVED' ? `
                      <button class="btn btn-primary btn-sm" onclick="openResolveModal(${m.id}, '${m.status}')">Resolve</button>
                    ` : ''}
                    <button class="btn btn-secondary btn-sm" onclick="viewResolutionHistory(${m.id})">Audit</button>
                  </div>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
  container.scrollIntoView({ behavior: 'smooth' });
}

// ============================================================
// 5. DISCREPANCIES WORKBENCH VIEW
// ============================================================
async function renderDiscrepancies(container) {
  container.innerHTML = '<div class="empty-state"><div class="spinner"></div> Loading discrepancies...</div>';

  try {
    const res = await window.api.getDiscrepancies(0, 50);
    const discrepancies = res?.content || [];
    AppState.discrepancies = discrepancies;

    container.innerHTML = `
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">Discrepancy Resolution Desk</h2>
            <div class="panel-subtitle">${discrepancies.length} flagged reconciliation exceptions</div>
          </div>
        </div>

        ${discrepancies.length === 0 ? `
          <div class="empty-state">
            <p>✓ No reconciliation discrepancies flagged. All settlement feeds are balanced.</p>
          </div>
        ` : `
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Match ID</th>
                  <th>Batch ID</th>
                  <th>Status</th>
                  <th>Reason / Discrepancy Note</th>
                  <th>Internal Ref</th>
                  <th>External ID</th>
                  <th class="text-right">Internal Amt</th>
                  <th class="text-right">Ext Gross</th>
                  <th class="text-right">Fee</th>
                  <th class="text-right">Ext Net</th>
                  <th class="text-center">Actions</th>
                </tr>
              </thead>
              <tbody>
                ${discrepancies.map(d => `
                  <tr>
                    <td class="font-mono">#${d.id}</td>
                    <td class="font-mono">#${d.batchId}</td>
                    <td>${getStatusBadge(d.status)}</td>
                    <td style="font-size:0.775rem; color:var(--text-secondary); max-width:180px;">${d.discrepancyReason || '—'}</td>
                    <td class="font-mono">${d.internalTxReference || '—'}</td>
                    <td class="font-mono">${d.externalTxId || '—'}</td>
                    <td class="text-right font-mono">${d.internalAmount !== null ? formatMoney(d.internalAmount) : '—'}</td>
                    <td class="text-right font-mono">${d.externalGrossAmount !== null ? formatMoney(d.externalGrossAmount) : '—'}</td>
                    <td class="text-right font-mono">${d.externalFee !== null ? formatMoney(d.externalFee) : '—'}</td>
                    <td class="text-right font-mono">${d.externalNetAmount !== null ? formatMoney(d.externalNetAmount) : '—'}</td>
                    <td class="text-center">
                      <div style="display:inline-flex; gap:0.35rem;">
                        ${d.status !== 'MATCHED' ? `
                          <button class="btn btn-primary btn-sm" onclick="openResolveModal(${d.id}, '${d.status}')">Resolve</button>
                        ` : ''}
                        <button class="btn btn-secondary btn-sm" onclick="viewResolutionHistory(${d.id})">History</button>
                      </div>
                    </td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        `}
      </div>
    `;
  } catch (err) {
    container.innerHTML = `<div class="panel" style="color:var(--color-danger);">Failed to load discrepancies: ${err.message}</div>`;
  }
}

function openResolveModal(matchId, currentStatus) {
  let validActionsHtml = '';

  if (currentStatus === 'FEE_DISCREPANCY') {
    validActionsHtml = `
      <option value="APPROVE_FEE_ADJUSTMENT" selected>APPROVE_FEE_ADJUSTMENT (Approve settlement fee variance)</option>
      <option value="ESCALATE_DISPUTE">ESCALATE_DISPUTE (Escalate to processor)</option>
      <option value="MANUAL_OVERRIDE_MATCH">MANUAL_OVERRIDE_MATCH (Manual override)</option>
    `;
  } else if (currentStatus === 'AMOUNT_MISMATCH') {
    validActionsHtml = `
      <option value="ACCEPT_AMOUNT_VARIANCE" selected>ACCEPT_AMOUNT_VARIANCE (Accept amount variance)</option>
      <option value="ESCALATE_DISPUTE">ESCALATE_DISPUTE (Escalate to processor)</option>
      <option value="MANUAL_OVERRIDE_MATCH">MANUAL_OVERRIDE_MATCH (Manual override)</option>
    `;
  } else if (currentStatus === 'UNMATCHED_INTERNAL' || currentStatus === 'UNMATCHED_EXTERNAL') {
    validActionsHtml = `
      <option value="DISMISS_ORPHAN" selected>DISMISS_ORPHAN (Dismiss orphan settlement record)</option>
      <option value="ESCALATE_DISPUTE">ESCALATE_DISPUTE (Escalate to processor)</option>
      <option value="MANUAL_OVERRIDE_MATCH">MANUAL_OVERRIDE_MATCH (Manual override)</option>
    `;
  } else {
    validActionsHtml = `
      <option value="ESCALATE_DISPUTE" selected>ESCALATE_DISPUTE (Escalate dispute)</option>
      <option value="MANUAL_OVERRIDE_MATCH">MANUAL_OVERRIDE_MATCH (Manual override)</option>
    `;
  }

  const html = `
    <form id="resolveForm">
      <div class="form-group">
        <label class="form-label">Match ID & Current Status</label>
        <input type="text" class="font-mono" value="#${matchId} (${currentStatus})" disabled />
      </div>

      <div class="form-group">
        <label class="form-label">Resolution Action *</label>
        <select id="modalResolutionAction" required>
          ${validActionsHtml}
        </select>
        <div class="form-help">Only valid actions compatible with status ${currentStatus} are permitted.</div>
      </div>

      <div class="form-group">
        <label class="form-label">Operator Name / ID *</label>
        <input type="text" id="modalResolvedBy" value="ops_officer_admin" required />
      </div>

      <div class="form-group">
        <label class="form-label">Audit Justification Notes *</label>
        <textarea id="modalNotes" rows="3" placeholder="Enter justification for this resolution action..." required></textarea>
        <div class="form-help">Audit log is permanent and append-only.</div>
      </div>
    </form>
  `;

  openModal(`Resolve Discrepancy #${matchId}`, html, async () => {
    const action = document.getElementById('modalResolutionAction').value;
    const resolvedBy = document.getElementById('modalResolvedBy').value.trim();
    const notes = document.getElementById('modalNotes').value.trim();

    if (!resolvedBy || !notes) {
      throw new Error('Operator and Notes are mandatory fields.');
    }

    const audit = await window.api.resolveDiscrepancy(matchId, {
      action,
      resolvedBy,
      notes,
    });

    showToast(`Discrepancy #${matchId} resolved: ${audit.newStatus}`, 'success', 'Resolution Success');
    navigateTo(AppState.activeScreen);
  }, 'Submit Resolution');
}

// ============================================================
// 6. RESOLUTION AUDIT HISTORY VIEW
// ============================================================
async function renderResolutionHistory(container) {
  container.innerHTML = `
    <div class="action-bar">
      <div style="display:flex; gap:0.5rem; max-width:380px; flex:1;">
        <input type="number" id="historyMatchLookupId" placeholder="Enter Match ID to inspect audit trail..." />
        <button class="btn btn-primary" onclick="lookupMatchHistory()">Inspect</button>
      </div>
    </div>

    <div id="historyTimelineContainer">
      <div class="empty-state">
        <p>Enter a Reconciliation Match ID to view its permanent, append-only resolution history.</p>
      </div>
    </div>
  `;
}

async function lookupMatchHistory() {
  const id = document.getElementById('historyMatchLookupId').value.trim();
  if (!id) return;
  viewResolutionHistory(parseInt(id, 10));
}

async function viewResolutionHistory(matchId) {
  const container = document.getElementById('historyTimelineContainer') || document.getElementById('contentArea');
  container.innerHTML = '<div class="panel"><div class="spinner"></div> Loading audit history...</div>';

  try {
    const history = await window.api.getResolutionHistory(matchId);

    if (!history || history.length === 0) {
      container.innerHTML = `
        <div class="panel">
          <div class="panel-header">
            <h2 class="panel-title">Audit Trail — Match #${matchId}</h2>
          </div>
          <div class="empty-state"><p>No resolution actions recorded for this match yet.</p></div>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">Permanent Resolution Audit Trail — Match #${matchId}</h2>
            <div class="panel-subtitle">${history.length} append-only audit entries recorded</div>
          </div>
        </div>

        <div class="timeline">
          ${history.map((entry) => `
            <div class="timeline-item">
              <div class="timeline-dot"></div>
              <div class="timeline-content">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.35rem;">
                  <span class="badge badge-purple">${entry.action}</span>
                  <span style="font-size:0.75rem; color:var(--text-muted); font-feature-settings:'tnum';">${formatDate(entry.resolvedAt)}</span>
                </div>
                <div style="font-size:0.825rem; margin-bottom:0.35rem;">
                  Transition: ${getStatusBadge(entry.previousStatus)} → ${getStatusBadge(entry.newStatus)}
                </div>
                <div style="font-size:0.775rem; color:var(--text-secondary); margin-bottom:0.35rem;">
                  Operator: <strong style="color:var(--text-primary); font-family:var(--font-mono);">${entry.resolvedBy}</strong>
                </div>
                <div style="font-size:0.775rem; background:rgba(0,0,0,0.25); padding:0.45rem 0.65rem; border-radius:var(--radius-xs); border-left:2px solid var(--accent-primary); color:var(--text-secondary);">
                  "${entry.notes}"
                </div>
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    `;
  } catch (err) {
    container.innerHTML = `<div class="panel" style="color:var(--color-danger);">Failed to load history: ${err.message}</div>`;
  }
}

// ============================================================
// 7. TRIAL BALANCE VIEW
// ============================================================
async function renderTrialBalance(container) {
  container.innerHTML = `
    <!-- Date Filter Bar -->
    <div class="panel" style="margin-bottom:1.25rem;">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Trial Balance Auditor</h2>
          <div class="panel-subtitle">Global double-entry invariant validation across all accounts</div>
        </div>
      </div>
      <div style="display:flex; gap:1rem; flex-wrap:wrap; align-items:flex-end;">
        <div class="form-group" style="margin-bottom:0; flex:1; min-width:180px;">
          <label class="form-label">From Date</label>
          <input type="datetime-local" id="tbFrom" />
        </div>
        <div class="form-group" style="margin-bottom:0; flex:1; min-width:180px;">
          <label class="form-label">To Date</label>
          <input type="datetime-local" id="tbTo" />
        </div>
        <button class="btn btn-primary" onclick="runTrialBalanceAudit()">Audit Invariants</button>
        <button class="btn btn-secondary" onclick="clearTrialBalanceFilters()">All-Time</button>
      </div>
    </div>

    <!-- Trial Balance Results Area -->
    <div id="trialBalanceResultArea">
      <div class="empty-state"><div class="spinner"></div> Computing global trial balance...</div>
    </div>
  `;

  runTrialBalanceAudit();
}

async function runTrialBalanceAudit() {
  const fromVal = document.getElementById('tbFrom')?.value;
  const toVal = document.getElementById('tbTo')?.value;
  const resultArea = document.getElementById('trialBalanceResultArea');
  if (!resultArea) return;

  resultArea.innerHTML = '<div class="empty-state"><div class="spinner"></div> Auditing system ledger...</div>';

  try {
    const report = await window.api.getTrialBalance(fromVal ? new Date(fromVal).toISOString() : null, toVal ? new Date(toVal).toISOString() : null);
    const diff = Math.abs(report.totalDebits - report.totalCredits);

    resultArea.innerHTML = `
      <div class="kpi-grid">
        <div class="kpi-card" style="border-color:${report.isBalanced ? 'var(--color-success-border)' : 'var(--color-danger-border)'};">
          <div class="kpi-label">Ledger Invariant State</div>
          <div class="kpi-value" style="color:${report.isBalanced ? 'var(--color-success)' : 'var(--color-danger)'};">
            ${report.isBalanced ? '✓ BALANCED' : '✕ IMBALANCED'}
          </div>
          <div class="kpi-subtext">Sum(Debits) ${report.isBalanced ? '==' : '≠'} Sum(Credits)</div>
        </div>

        <div class="kpi-card">
          <div class="kpi-label">Total System Debits</div>
          <div class="kpi-value font-mono" style="color:var(--color-danger);">${formatMoney(report.totalDebits)}</div>
          <div class="kpi-subtext">All debited funds in scope</div>
        </div>

        <div class="kpi-card">
          <div class="kpi-label">Total System Credits</div>
          <div class="kpi-value font-mono" style="color:var(--color-success);">${formatMoney(report.totalCredits)}</div>
          <div class="kpi-subtext">All credited funds in scope</div>
        </div>

        <div class="kpi-card">
          <div class="kpi-label">Variance</div>
          <div class="kpi-value font-mono" style="color:${diff === 0 ? 'var(--color-success)' : 'var(--color-danger)'};">
            ${formatMoney(diff)}
          </div>
          <div class="kpi-subtext">${report.entryCount} entries across ${report.accountCount} accounts</div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">Audit Proof Details</h2>
            <div class="panel-subtitle">Generated: ${formatDate(report.generatedAt)}</div>
          </div>
          <span class="badge ${report.isBalanced ? 'badge-success' : 'badge-danger'}">${report.isBalanced ? 'INVARIANT VERIFIED' : 'FAILED'}</span>
        </div>
        <div style="font-size:0.825rem; color:var(--text-secondary); line-height:1.7;">
          <p>• <strong>Scope Window:</strong> ${report.from ? formatDate(report.from) : 'System Inception'} through ${report.to ? formatDate(report.to) : 'Present'}</p>
          <p>• <strong>Double-Entry Rule:</strong> Every financial transfer produces offsetting and equal debit and credit ledger legs.</p>
          <p>• <strong>Accounts Audited:</strong> ${report.accountCount} accounts with active ledger entries.</p>
        </div>
      </div>
    `;
  } catch (err) {
    resultArea.innerHTML = `<div class="panel" style="color:var(--color-danger);">Trial balance computation error: ${err.message}</div>`;
  }
}

function clearTrialBalanceFilters() {
  const f = document.getElementById('tbFrom');
  const t = document.getElementById('tbTo');
  if (f) f.value = '';
  if (t) t.value = '';
  runTrialBalanceAudit();
}

// ============================================================
// 8. HISTORICAL ACCOUNT STATEMENTS VIEW
// ============================================================
async function renderStatements(container) {
  container.innerHTML = `
    <div class="panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Generate Account Statement</h2>
          <div class="panel-subtitle">Reconstruct historical opening balance and chronological ledger entries</div>
        </div>
      </div>
      <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(180px, 1fr)); gap:1rem; align-items:flex-end;">
        <div class="form-group" style="margin-bottom:0;">
          <label class="form-label">Account ID *</label>
          <input type="number" id="stmtAccountId" placeholder="e.g. 1" required />
        </div>
        <div class="form-group" style="margin-bottom:0;">
          <label class="form-label">From Date (Optional)</label>
          <input type="datetime-local" id="stmtFrom" />
        </div>
        <div class="form-group" style="margin-bottom:0;">
          <label class="form-label">To Date (Optional)</label>
          <input type="datetime-local" id="stmtTo" />
        </div>
        <button class="btn btn-primary" onclick="generateStatement()">Generate Statement</button>
      </div>
    </div>

    <!-- Statement Result Area -->
    <div id="statementResultArea">
      <div class="empty-state">
        <p>Select an Account ID and date window to reconstruct opening and running balances.</p>
      </div>
    </div>
  `;
}

async function viewAccountStatement(accountId) {
  navigateTo('statements');
  setTimeout(() => {
    const accInput = document.getElementById('stmtAccountId');
    if (accInput) {
      accInput.value = accountId;
      generateStatement();
    }
  }, 100);
}

async function generateStatement() {
  const accountId = document.getElementById('stmtAccountId')?.value.trim();
  const fromVal = document.getElementById('stmtFrom')?.value;
  const toVal = document.getElementById('stmtTo')?.value;
  const resultArea = document.getElementById('statementResultArea');
  if (!resultArea) return;

  if (!accountId) {
    showToast('Please specify an Account ID', 'warning');
    return;
  }

  resultArea.innerHTML = '<div class="empty-state"><div class="spinner"></div> Reconstructing ledger statement...</div>';

  try {
    const stmt = await window.api.getAccountStatement(
      accountId,
      fromVal ? new Date(fromVal).toISOString() : null,
      toVal ? new Date(toVal).toISOString() : null
    );

    resultArea.innerHTML = `
      <div class="panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">Statement — Account #${stmt.accountId} (${stmt.accountNumber})</h2>
            <div class="panel-subtitle">
              Period: ${stmt.from ? formatDate(stmt.from) : 'Account Inception'} through ${stmt.to ? formatDate(stmt.to) : 'Present'}
            </div>
          </div>
          <div>
            ${stmt.isClosingBalanceVerified ? `
              <span class="badge badge-success">✓ BALANCE VERIFIED</span>
            ` : `
              <span class="badge badge-warning">HISTORICAL SNAPSHOT</span>
            `}
          </div>
        </div>

        <!-- Balance Reconstruction Metric Grid -->
        <div class="kpi-grid" style="margin-bottom:1.5rem;">
          <div class="kpi-card">
            <div class="kpi-label">Reconstructed Opening Balance</div>
            <div class="kpi-value font-mono" style="font-size:1.35rem;">${formatMoney(stmt.openingBalance, stmt.currency)}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Total Credited in Window</div>
            <div class="kpi-value font-mono" style="font-size:1.35rem; color:var(--color-success);">+${formatMoney(stmt.totalCredited, stmt.currency)}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Total Debited in Window</div>
            <div class="kpi-value font-mono" style="font-size:1.35rem; color:var(--color-danger);">${formatMoney(stmt.totalDebited, stmt.currency)}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Closing Balance</div>
            <div class="kpi-value font-mono" style="font-size:1.35rem; color:var(--accent-primary);">${formatMoney(stmt.closingBalance, stmt.currency)}</div>
          </div>
        </div>

        <!-- Entries Table -->
        <div class="panel-header" style="margin-bottom:0.75rem;">
          <h3 class="panel-title" style="font-size:0.875rem;">Chronological Ledger Entries</h3>
        </div>
        ${stmt.entries.length === 0 ? `
          <div class="empty-state"><p>No ledger entries found for this account within the selected time window.</p></div>
        ` : `
          <div class="table-container">
            <table>
              <thead>
                <tr>
                  <th>Entry ID</th>
                  <th>Timestamp</th>
                  <th>Tx Reference</th>
                  <th>Type</th>
                  <th class="text-right">Amount</th>
                  <th class="text-right">Running Balance</th>
                </tr>
              </thead>
              <tbody>
                ${stmt.entries.map(e => {
                  const isCredit = e.entryType === 'CREDIT';
                  return `
                    <tr>
                      <td class="font-mono">#${e.ledgerEntryId}</td>
                      <td>${formatDate(e.createdAt)}</td>
                      <td class="font-mono">${e.transactionReference || '—'}</td>
                      <td>
                        <span class="badge ${isCredit ? 'badge-success' : 'badge-danger'}">${e.entryType}</span>
                      </td>
                      <td class="text-right font-mono" style="font-weight:600; color:${isCredit ? 'var(--color-success)' : 'var(--color-danger)'};">
                        ${isCredit ? '+' : '-'}${formatMoney(e.amount, stmt.currency)}
                      </td>
                      <td class="text-right font-mono" style="font-weight:700;">
                        ${formatMoney(e.runningBalance, stmt.currency)}
                      </td>
                    </tr>
                  `;
                }).join('')}
              </tbody>
            </table>
          </div>
        `}
      </div>
    `;
  } catch (err) {
    resultArea.innerHTML = `<div class="panel" style="color:var(--color-danger);">Failed to generate statement: ${err.message}</div>`;
  }
}

// ============================================================
// MODAL FORMS
// ============================================================
function openCreateAccountModal() {
  const html = `
    <form id="createAccountForm">
      <div class="form-group">
        <label class="form-label">Full Name *</label>
        <input type="text" id="accName" placeholder="e.g. Alex Mercer" required />
      </div>
      <div class="form-group">
        <label class="form-label">User Email Address *</label>
        <input type="email" id="accEmail" placeholder="e.g. alex@example.com" required />
        <div class="form-help">Reuses existing user profile if email is already registered.</div>
      </div>
      <div class="form-group">
        <label class="form-label">Account Currency *</label>
        <select id="accCurrency">
          <option value="INR" selected>INR (Indian Rupee)</option>
          <option value="USD">USD (US Dollar)</option>
          <option value="EUR">EUR (Euro)</option>
          <option value="GBP">GBP (British Pound)</option>
        </select>
      </div>
    </form>
  `;

  openModal('Open New Account', html, async () => {
    const name = document.getElementById('accName').value.trim();
    const email = document.getElementById('accEmail').value.trim();
    const currency = document.getElementById('accCurrency').value;

    if (!name || !email) throw new Error('Name and email are required.');

    const newAcc = await window.api.createAccount({ name, email, currency });
    addCachedAccount(newAcc);
    showToast(`Account ${newAcc.accountNumber} created with 0.00 base balance`, 'success');
    navigateTo('accounts');
  }, 'Open Account');
}

function openTransferModal(preselectedSourceId = null) {
  const html = `
    <form id="modalTransferForm">
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:0.75rem; margin-bottom:0.75rem;">
        <div class="form-group" style="margin-bottom:0;">
          <label class="form-label">Source Account ID *</label>
          <input type="number" id="mTxSourceId" value="${preselectedSourceId || ''}" placeholder="Source ID" required />
        </div>
        <div class="form-group" style="margin-bottom:0;">
          <label class="form-label">Destination Account ID *</label>
          <input type="number" id="mTxDestId" placeholder="Destination ID" required />
        </div>
      </div>
      <div style="display:grid; grid-template-columns:2fr 1fr; gap:0.75rem; margin-bottom:0.75rem;">
        <div class="form-group" style="margin-bottom:0;">
          <label class="form-label">Transfer Amount *</label>
          <input type="number" step="0.01" min="0.01" id="mTxAmount" placeholder="100.00" class="font-mono" style="font-weight:600;" required />
        </div>
        <div class="form-group" style="margin-bottom:0;">
          <label class="form-label">Currency *</label>
          <select id="mTxCurrency">
            <option value="INR" selected>INR</option>
            <option value="USD">USD</option>
            <option value="EUR">EUR</option>
            <option value="GBP">GBP</option>
          </select>
        </div>
      </div>
      <div class="form-group" style="margin-bottom:0;">
        <label class="form-label">Idempotency Key (UUID)</label>
        <input type="text" id="mTxIdempotencyKey" class="font-mono" value="${generateUUID()}" required />
      </div>
    </form>
  `;

  openModal('Execute Fund Transfer', html, async () => {
    const sourceAccountId = parseInt(document.getElementById('mTxSourceId').value, 10);
    const destinationAccountId = parseInt(document.getElementById('mTxDestId').value, 10);
    const amount = parseFloat(document.getElementById('mTxAmount').value);
    const currency = document.getElementById('mTxCurrency').value;
    const idempotencyKey = document.getElementById('mTxIdempotencyKey').value.trim();

    if (sourceAccountId === destinationAccountId) {
      throw new Error('Source and Destination account cannot be identical.');
    }

    const tx = await window.api.transfer({
      sourceAccountId,
      destinationAccountId,
      amount,
      currency,
      idempotencyKey,
    });

    showToast(`Transfer successful: ${tx.transactionReference}`, 'success');
    refreshAccountBalance(sourceAccountId);
    refreshAccountBalance(destinationAccountId);
    refreshLedgerHealth();
  }, 'Execute Transfer');
}

function openIngestBatchModal() {
  const sampleBatchRef = 'BATCH-' + generateUUID().substring(0, 8).toUpperCase();
  const sampleRecords = [
    {
      externalTxId: 'EXT-TX-101',
      internalTxReference: 'TX-REF-001',
      grossAmount: 1000.00,
      fee: 25.00,
      currency: 'INR',
      settlementDate: new Date().toISOString()
    }
  ];

  const html = `
    <form id="ingestBatchForm">
      <div class="form-group">
        <label class="form-label">Batch Reference *</label>
        <input type="text" id="mBatchRef" value="${sampleBatchRef}" class="font-mono" required />
      </div>
      <div class="form-group">
        <label class="form-label">Source Processor Provider *</label>
        <input type="text" id="mBatchProvider" value="STRIPE" required />
      </div>
      <div class="form-group">
        <label class="form-label">Settlement Records (JSON Array) *</label>
        <textarea id="mBatchRecordsJson" rows="7" class="font-mono" style="font-size:0.775rem;" required>${JSON.stringify(sampleRecords, null, 2)}</textarea>
        <div class="form-help">Format: Array of { externalTxId, internalTxReference, grossAmount, fee, currency, settlementDate }</div>
      </div>
    </form>
  `;

  openModal('Ingest Settlement Batch', html, async () => {
    const batchReference = document.getElementById('mBatchRef').value.trim();
    const sourceProvider = document.getElementById('mBatchProvider').value.trim();
    const jsonStr = document.getElementById('mBatchRecordsJson').value.trim();

    let records;
    try {
      records = JSON.parse(jsonStr);
      if (!Array.isArray(records)) throw new Error('Records must be a JSON array.');
    } catch (e) {
      throw new Error('Invalid JSON format: ' + e.message);
    }

    const batch = await window.api.ingestBatch({
      batchReference,
      sourceProvider,
      records,
    });

    addCachedBatch(batch);
    showToast(`Batch ${batch.batchReference} ingested with ${batch.totalRecords} records`, 'success');
    navigateTo('reconciliation');
  }, 'Ingest Batch');
}

// --- Application Initialization ---
window.addEventListener('DOMContentLoaded', () => {
  const hash = window.location.hash.replace('#', '') || 'dashboard';
  navigateTo(hash);
  refreshLedgerHealth();
});

window.addEventListener('hashchange', () => {
  const hash = window.location.hash.replace('#', '') || 'dashboard';
  if (hash !== AppState.activeScreen) {
    navigateTo(hash);
  }
});
