const FORMS = ['DISTANCE_EDUCATION', 'FULL_TIME_EDUCATION', 'EVENING_CLASSES'];
const SEMESTERS = ['SECOND', 'THIRD', 'SEVENTH', 'EIGHTH'];
const COLORS = ['GREEN', 'YELLOW', 'ORANGE'];
const COUNTRIES = ['USA', 'CHINA', 'VATICAN', 'SOUTH_KOREA', 'NORTH_KOREA'];
const state = { page: null, groups: new Map() };
const byId = id => document.getElementById(id);

function fillSelect(id, values) {
    const select = byId(id);
    values.forEach(value => select.add(new Option(value, value)));
}
fillSelect('education-form', FORMS);
fillSelect('semester', SEMESTERS);
fillSelect('hair-color', COLORS);
fillSelect('nationality', COUNTRIES);

async function api(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers }
    });
    const text = await response.text();
    let body = null;
    if (text) {
        try { body = JSON.parse(text); } catch { body = { message: text }; }
    }
    if (!response.ok) {
        const error = new Error(body?.message || `Сервис вернул HTTP ${response.status}`);
        error.status = response.status;
        error.body = body;
        throw error;
    }
    return body;
}

function showNotice(message, error = false) {
    const notice = byId('notice');
    notice.textContent = message;
    notice.classList.toggle('error', error);
    notice.hidden = false;
    clearTimeout(showNotice.timer);
    showNotice.timer = setTimeout(() => { notice.hidden = true; }, error ? 9000 : 3500);
}

function showError(error) {
    const body = error.body || {};
    const fields = body.violations || body.details;
    const suffix = fields ? ' — ' + Object.entries(fields).map(([key, value]) => `${key}: ${value}`).join('; ') : '';
    showNotice(`${body.code ? `[${body.code}] ` : ''}${error.message}${suffix}`, true);
}

function lines(id) {
    return byId(id).value.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
}

async function loadGroups() {
    const params = new URLSearchParams({ page: byId('page').value, size: byId('size').value });
    lines('sort').forEach(value => params.append('sort', value));
    lines('filter').forEach(value => params.append('filter', value));
    try {
        const page = await api(`/api/study-groups?${params}`);
        state.page = page;
        state.groups = new Map(page.content.map(group => [group.id, group]));
        renderGroups(page);
    } catch (error) { showError(error); }
}

function renderGroups(page) {
    const body = byId('groups-body');
    body.replaceChildren();
    if (!page.content.length) {
        const row = body.insertRow();
        const cell = row.insertCell();
        cell.colSpan = 8;
        cell.className = 'muted';
        cell.textContent = 'На этой странице нет групп.';
    }
    page.content.forEach(group => {
        const row = body.insertRow();
        addCell(row, group.id);
        const name = addCell(row);
        addStrong(name, group.name);
        appendText(name, group.semesterEnum || 'Семестр не указан', 'subtle');
        addCell(row, `${group.coordinates.x}; ${group.coordinates.y}`);
        addCell(row, group.studentsCount.toLocaleString('ru-RU'));
        addCell(row, readable(group.formOfEducation));
        const admin = addCell(row);
        if (group.groupAdmin) {
            addStrong(admin, group.groupAdmin.name);
            appendText(admin, [readable(group.groupAdmin.nationality), readable(group.groupAdmin.hairColor)].filter(Boolean).join(' · '), 'subtle');
            if (group.groupAdmin.location) appendText(admin, `Место: ${group.groupAdmin.location.x}; ${group.groupAdmin.location.y}; ${group.groupAdmin.location.z}`, 'subtle');
        } else admin.textContent = '—';
        addCell(row, new Date(group.creationDate).toLocaleString('ru-RU'));
        const actions = addCell(row);
        actions.className = 'row-actions';
        const edit = document.createElement('button'); edit.textContent = 'Изменить'; edit.onclick = () => openEditor(group);
        const remove = document.createElement('button'); remove.textContent = 'Удалить'; remove.className = 'danger'; remove.onclick = () => deleteGroup(group);
        actions.append(edit, remove);
    });
    const humanPage = page.totalPages === 0 ? 0 : page.page + 1;
    byId('page-summary').textContent = `Страница ${humanPage} из ${page.totalPages} · всего ${page.totalElements}`;
    byId('previous-page').disabled = page.page <= 0;
    byId('next-page').disabled = page.page + 1 >= page.totalPages;
}

function addCell(row, text) { const cell = row.insertCell(); if (text !== undefined) cell.textContent = text; return cell; }
function addStrong(parent, text) { const element = document.createElement('strong'); element.textContent = text; parent.append(element); }
function appendText(parent, text, className) { if (!text) return; const element = document.createElement('div'); element.className = className; element.textContent = text; parent.append(element); }
function readable(value) { return value ? value.toLowerCase().split('_').map(word => word[0].toUpperCase() + word.slice(1)).join(' ') : ''; }

function resetEditor() {
    byId('group-form').reset();
    byId('edit-id').value = '';
    byId('dialog-title').textContent = 'Новая группа';
    byId('education-form').value = 'DISTANCE_EDUCATION';
    toggleAdmin(); toggleLocation();
}

function openEditor(group = null) {
    resetEditor();
    if (group) {
        byId('edit-id').value = group.id;
        byId('dialog-title').textContent = `Изменить группу #${group.id}`;
        byId('group-name').value = group.name;
        byId('coordinate-x').value = group.coordinates.x;
        byId('coordinate-y').value = group.coordinates.y;
        byId('students-count').value = group.studentsCount;
        byId('education-form').value = group.formOfEducation;
        byId('semester').value = group.semesterEnum || '';
        byId('has-admin').checked = Boolean(group.groupAdmin); toggleAdmin();
        if (group.groupAdmin) {
            byId('person-name').value = group.groupAdmin.name;
            byId('birthday').value = group.groupAdmin.birthday || '';
            byId('hair-color').value = group.groupAdmin.hairColor || '';
            byId('nationality').value = group.groupAdmin.nationality;
            byId('has-location').checked = Boolean(group.groupAdmin.location); toggleLocation();
            if (group.groupAdmin.location) {
                byId('location-x').value = group.groupAdmin.location.x;
                byId('location-y').value = group.groupAdmin.location.y;
                byId('location-z').value = group.groupAdmin.location.z;
            }
        }
    }
    byId('group-dialog').showModal();
}

function toggleAdmin() {
    byId('admin-fields').disabled = !byId('has-admin').checked;
    byId('person-name').required = byId('has-admin').checked;
    if (byId('has-admin').checked && !byId('nationality').value) byId('nationality').value = 'USA';
}
function toggleLocation() {
    const enabled = byId('has-admin').checked && byId('has-location').checked;
    ['location-x', 'location-y', 'location-z'].forEach(id => { byId(id).disabled = !enabled; byId(id).required = enabled; });
    byId('location-fields').setAttribute('aria-disabled', String(!enabled));
}

function formPayload() {
    const payload = {
        name: byId('group-name').value,
        coordinates: { x: Number(byId('coordinate-x').value), y: Number(byId('coordinate-y').value) },
        studentsCount: Number(byId('students-count').value),
        formOfEducation: byId('education-form').value
    };
    if (byId('semester').value) payload.semesterEnum = byId('semester').value;
    if (byId('has-admin').checked) {
        payload.groupAdmin = { name: byId('person-name').value, nationality: byId('nationality').value };
        if (byId('birthday').value) payload.groupAdmin.birthday = byId('birthday').value;
        if (byId('hair-color').value) payload.groupAdmin.hairColor = byId('hair-color').value;
        if (byId('has-location').checked) payload.groupAdmin.location = {
            x: Number(byId('location-x').value), y: Number(byId('location-y').value), z: Number(byId('location-z').value)
        };
    }
    return payload;
}

async function saveGroup(event) {
    event.preventDefault();
    const id = byId('edit-id').value;
    try {
        const group = await api(id ? `/api/study-groups/${id}` : '/api/study-groups', {
            method: id ? 'PUT' : 'POST', body: JSON.stringify(formPayload())
        });
        byId('group-dialog').close();
        showNotice(id ? `Группа #${id} обновлена.` : `Группа #${group.id} создана.`);
        await loadGroups();
    } catch (error) { showError(error); }
}

async function deleteGroup(group) {
    if (!confirm(`Удалить группу «${group.name}» (#${group.id})?`)) return;
    try { await api(`/api/study-groups/${group.id}`, { method: 'DELETE' }); showNotice(`Группа #${group.id} удалена.`); await loadGroups(); }
    catch (error) { showError(error); }
}

function renderSpecial(value) {
    const box = byId('special-result'); box.classList.remove('muted'); box.replaceChildren();
    if (Array.isArray(value)) {
        if (!value.length) { box.textContent = 'Совпадений нет.'; return; }
        value.forEach(group => appendText(box, `#${group.id} · ${group.name} · ${group.studentsCount} студентов`, ''));
    } else if (value?.id) box.textContent = `#${value.id} · ${value.name} · администратор: ${value.groupAdmin?.name || '—'}`;
    else box.textContent = `Количество: ${value.count} (сравнение: ${value.greaterThan})`;
}

byId('query-form').addEventListener('submit', event => { event.preventDefault(); loadGroups(); });
byId('reset-query').onclick = () => { byId('page').value = 0; byId('size').value = 10; byId('sort').value = 'id,asc'; byId('filter').value = ''; loadGroups(); };
byId('previous-page').onclick = () => { byId('page').value = Math.max(0, Number(byId('page').value) - 1); loadGroups(); };
byId('next-page').onclick = () => { byId('page').value = Number(byId('page').value) + 1; loadGroups(); };
byId('new-group').onclick = () => openEditor();
byId('close-dialog').onclick = byId('cancel-dialog').onclick = () => byId('group-dialog').close();
byId('has-admin').onchange = () => { toggleAdmin(); toggleLocation(); };
byId('has-location').onchange = toggleLocation;
byId('group-form').addEventListener('submit', saveGroup);

byId('get-by-id-form').onsubmit = async event => { event.preventDefault(); try { renderSpecial(await api(`/api/study-groups/${byId('lookup-id').value}`)); } catch (error) { showError(error); } };
byId('max-admin').onclick = async () => { try { renderSpecial(await api('/api/study-groups/group-admin/max')); } catch (error) { showError(error); } };
byId('count-admin-form').onsubmit = async event => { event.preventDefault(); try { renderSpecial(await api(`/api/study-groups/group-admin/count-greater?adminName=${encodeURIComponent(byId('admin-name').value)}`)); } catch (error) { showError(error); } };
byId('contains-form').onsubmit = async event => { event.preventDefault(); try { renderSpecial(await api(`/api/study-groups/name/contains?substring=${encodeURIComponent(byId('substring').value)}`)); } catch (error) { showError(error); } };
byId('isu-form').onsubmit = async event => {
    event.preventDefault(); const id = byId('isu-group-id').value; const form = byId('isu-form-value').value;
    try { const result = await api(`/isu/group/${id}/change-edu-form/${form}`, { method: 'POST' }); byId('isu-result').textContent = `Группа #${id}: новая форма — ${readable(result.formOfEducation)}.`; byId('isu-result').classList.remove('muted'); await loadGroups(); }
    catch (error) { showError(error); }
};
byId('expel-all').onclick = async () => {
    const id = byId('isu-group-id').value;
    if (!id) { showNotice('Укажите ID группы для операции ИСУ.', true); return; }
    if (!confirm(`Отчислить всех студентов и удалить пустую группу #${id}?`)) return;
    try { const result = await api(`/isu/group/${id}/expel-all`, { method: 'POST' }); byId('isu-result').textContent = result.message; byId('isu-result').classList.remove('muted'); await loadGroups(); }
    catch (error) { showError(error); }
};

loadGroups();
