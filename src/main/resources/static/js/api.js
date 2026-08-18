/**
 * ClearLedger API Client
 * Wraps all /api/v1/* endpoints with consistent error handling and formatting.
 */

const API_BASE = '/api/v1';

class ApiClient {
  async request(endpoint, options = {}) {
    const url = `${API_BASE}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...options.headers,
    };

    const config = {
      ...options,
      headers,
    };

    if (config.body && typeof config.body === 'object') {
      config.body = JSON.stringify(config.body);
    }

    try {
      const response = await fetch(url, config);
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        const errorMsg = data?.message || data?.error || `HTTP ${response.status}: ${response.statusText}`;
        const error = new Error(errorMsg);
        error.status = response.status;
        error.payload = data;
        throw error;
      }

      return data;
    } catch (err) {
      console.error(`API Error on [${options.method || 'GET'}] ${url}:`, err);
      throw err;
    }
  }

  // --- Accounts API ---
  createAccount(payload) {
    return this.request('/accounts', {
      method: 'POST',
      body: payload,
    });
  }

  getAccount(id) {
    return this.request(`/accounts/${id}`);
  }

  getAccountBalance(id) {
    return this.request(`/accounts/${id}/balance`);
  }

  getAccountTransactions(id, page = 0, size = 20) {
    return this.request(`/accounts/${id}/transactions?page=${page}&size=${size}&sort=createdAt,desc`);
  }

  // --- Transactions API ---
  transfer(payload) {
    return this.request('/transactions', {
      method: 'POST',
      body: payload,
    });
  }

  // --- Reconciliation & Settlement API ---
  ingestBatch(payload) {
    return this.request('/reconciliation/batches', {
      method: 'POST',
      body: payload,
    });
  }

  reconcileBatch(batchId) {
    return this.request(`/reconciliation/batches/${batchId}/reconcile`, {
      method: 'POST',
    });
  }

  getBatchSummary(batchId) {
    return this.request(`/reconciliation/batches/${batchId}/summary`);
  }

  getDiscrepancies(page = 0, size = 20) {
    return this.request(`/reconciliation/discrepancies?page=${page}&size=${size}`);
  }

  // --- Discrepancy Resolution API ---
  resolveDiscrepancy(matchId, payload) {
    return this.request(`/reconciliation/matches/${matchId}/resolve`, {
      method: 'POST',
      body: payload,
    });
  }

  getResolutionHistory(matchId) {
    return this.request(`/reconciliation/matches/${matchId}/history`);
  }

  // --- Audit & Trial Balance API ---
  getTrialBalance(from, to) {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const query = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/audit/trial-balance${query}`);
  }

  getAccountStatement(accountId, from, to) {
    const params = new URLSearchParams();
    if (from) params.append('from', from);
    if (to) params.append('to', to);
    const query = params.toString() ? `?${params.toString()}` : '';
    return this.request(`/audit/accounts/${accountId}/statement${query}`);
  }
}

window.api = new ApiClient();
