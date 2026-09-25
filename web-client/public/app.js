const FORMS = ['DISTANCE_EDUCATION', 'FULL_TIME_EDUCATION', 'EVENING_CLASSES'];
const SEMESTERS = ['SECOND', 'THIRD', 'SEVENTH', 'EIGHTH'];
const COLORS = ['GREEN', 'YELLOW', 'ORANGE'];
const COUNTRIES = ['USA', 'CHINA', 'VATICAN', 'SOUTH_KOREA', 'NORTH_KOREA'];
const LABELS = {
    DISTANCE_EDUCATION: 'Дистанционная',
    FULL_TIME_EDUCATION: 'Очная',
    EVENING_CLASSES: 'Вечерняя',
    SECOND: 'Второй',
    THIRD: 'Третий',
    SEVENTH: 'Седьмой',
    EIGHTH: 'Восьмой',
    GREEN: 'Зелёный',
    YELLOW: 'Жёлтый',
    ORANGE: 'Оранжевый',
    USA: 'США',
    CHINA: 'Китай',
    VATICAN: 'Ватикан',
    SOUTH_KOREA: 'Южная Корея',
    NORTH_KOREA: 'Северная Корея'
};
const TABLE_LABELS = ['ID', 'Группа', 'Координаты', 'Студенты', 'Обучение', 'Администратор', 'Создана', 'Действия'];
const FIELD_LABELS = {
    request: 'Тело запроса', id: 'Идентификатор группы', groupId: 'Идентификатор группы', name: 'Название группы',
    coordinates: 'Координаты', 'coordinates.x': 'Координата X', 'coordinates.y': 'Координата Y',
    creationDate: 'Дата создания', studentsCount: 'Количество студентов', formOfEducation: 'Форма обучения',
    newForm: 'Новая форма обучения', semesterEnum: 'Семестр', groupAdmin: 'Администратор',
    'groupAdmin.name': 'Имя администратора', 'groupAdmin.birthday': 'Дата рождения администратора',
    'groupAdmin.hairColor': 'Цвет волос', 'groupAdmin.nationality': 'Гражданство',
    'groupAdmin.location': 'Местоположение', 'groupAdmin.location.x': 'Местоположение: X',
    'groupAdmin.location.y': 'Местоположение: Y', 'groupAdmin.location.z': 'Местоположение: Z',
    page: 'Номер страницы', size: 'Размер страницы', adminName: 'Имя администратора', substring: 'Подстрока',
    parameter: 'Параметр', field: 'Поле', value: 'Значение', expectedType: 'Ожидаемый тип',
    filter: 'Фильтр', operator: 'Оператор', sort: 'Сортировка', min: 'Минимум', max: 'Максимум',
    upstreamStatus: 'Код ответа сервиса', status: 'Код ответа', received: 'Полученный формат'
};
const TYPE_LABELS = {
    int: 'целое число', Integer: 'целое число', Long: 'целое число', Float: 'конечное число',
    Double: 'конечное число', String: 'строка', Instant: 'дата и время', ZonedDateTime: 'дата и время с часовым поясом',
    FormOfEducation: 'форма обучения', Semester: 'семестр', Color: 'цвет волос', Country: 'страна'
};
const state = { page: null, groups: new Map(), loading: false };
const byId = id => document.getElementById(id);

function readable(value) {
    if (!value) return '';
    return LABELS[value] || value.toLowerCase().split('_').map(word => word[0].toUpperCase() + word.slice(1)).join(' ');
}

function fillSelect(id, values) {
    const select = byId(id);
    values.forEach(value => select.add(new Option(readable(value), value)));
}

fillSelect('education-form', FORMS);
fillSelect('semester', SEMESTERS);
fillSelect('hair-color', COLORS);
fillSelect('nationality', COUNTRIES);

async function api(url, options = {}) {
    let response;
    let text;
    try {
        response = await fetch(url, {
            ...options,
            headers: {
                Accept: 'application/json',
                ...(options.body ? { 'Content-Type': 'application/json' } : {}),
                ...options.headers
            }
        });
        text = await response.text();
    } catch (cause) {
        throw new Error('Не удалось связаться с сервером. Проверьте подключение и доступность сервиса.', { cause });
    }
    let body = null;
    if (text) {
        const isJson = response.headers.get('content-type')?.includes('application/json');
        try {
            body = isJson ? JSON.parse(text) : null;
        } catch {
            body = null;
        }
    }
    if (!response.ok) {
        const error = new Error(typeof body?.message === 'string' ? body.message : httpErrorMessage(response.status));
        error.status = response.status;
        error.body = body;
        throw error;
    }
    if (response.status !== 204 && (!body || typeof body !== 'object')) {
        throw new Error('Сервер вернул некорректный ответ. Повторите попытку позже.');
    }
    return body;
}

function httpErrorMessage(status) {
    if (status === 413) return 'Тело запроса слишком велико.';
    if (status === 502 || status === 503 || status === 504) return 'Сервис временно недоступен. Повторите попытку позже.';
    return `Не удалось выполнить запрос. Код ответа сервера: ${status}.`;
}

function showNotice(message, error = false) {
    const notice = byId('notice');
    const dialog = byId('group-dialog');
    if (dialog.open) dialog.append(notice);
    notice.textContent = message;
    notice.classList.toggle('error', error);
    notice.setAttribute('role', error ? 'alert' : 'status');
    notice.hidden = false;
    clearTimeout(showNotice.timer);
    showNotice.timer = setTimeout(() => { notice.hidden = true; }, error ? 9000 : 3500);
}

function showError(error) {
    const body = error.body || {};
    const fields = Object.entries(body.violations || body.details || {});
    const suffix = fields.length ? ' — ' + fields.map(([key, value]) => {
        const label = FIELD_LABELS[key] || key;
        const text = key === 'expectedType' ? (TYPE_LABELS[value] || value)
            : key === 'field' || key === 'parameter' ? (FIELD_LABELS[value] || value)
                : LABELS[value] || value;
        return `${label}: ${text}`;
    }).join('; ') : '';
    const message = error instanceof TypeError || error instanceof SyntaxError
        ? 'Не удалось выполнить действие. Повторите попытку.' : error.message;
    showNotice(`${message}${suffix}`, true);
}

function lines(id) {
    return byId(id).value.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
}

function pluralGroups(value) {
    const lastTwo = value % 100;
    const last = value % 10;
    if (lastTwo >= 11 && lastTwo <= 14) return 'групп';
    if (last === 1) return 'группа';
    if (last >= 2 && last <= 4) return 'группы';
    return 'групп';
}

function setCollectionLoading(loading) {
    state.loading = loading;
    const table = byId('collection-table');
    table.classList.toggle('is-loading', loading);
    table.setAttribute('aria-busy', String(loading));
    byId('query-form').querySelector('button[type="submit"]').disabled = loading;
    byId('reset-query').disabled = loading;
    if (loading) byId('collection-status').textContent = 'Обновляем данные…';
    updatePager();
}

function updatePager() {
    const page = state.page;
    byId('previous-page').disabled = state.loading || !page || page.page <= 0;
    byId('next-page').disabled = state.loading || !page || page.page + 1 >= page.totalPages;
}

function updateOverview(page) {
    const visible = page.content.length;
    const humanPage = page.totalPages === 0 ? 0 : page.page + 1;
    byId('metric-total').textContent = page.totalElements.toLocaleString('ru-RU');
    byId('metric-visible').textContent = visible.toLocaleString('ru-RU');
    byId('metric-page').textContent = page.totalPages ? `${humanPage} / ${page.totalPages}` : '0';
    const filtered = lines('filter').length > 0;
    byId('collection-status').textContent = `${page.totalElements} ${pluralGroups(page.totalElements)}${filtered ? ' с учётом фильтров' : ' в коллекции'}`;
}

async function loadGroups() {
    const params = new URLSearchParams({ page: byId('page').value, size: byId('size').value });
    lines('sort').forEach(value => params.append('sort', value));
    lines('filter').forEach(value => params.append('filter', value));
    setCollectionLoading(true);
    try {
        const page = await api(`/api/study-groups?${params}`);
        state.page = page;
        state.groups = new Map(page.content.map(group => [group.id, group]));
        renderGroups(page);
        updateOverview(page);
        if (byId('notice').classList.contains('error')) byId('notice').hidden = true;
    } catch (error) {
        state.page = null;
        state.groups.clear();
        renderCollectionError();
        byId('metric-total').textContent = '—';
        byId('metric-visible').textContent = '—';
        byId('metric-page').textContent = '—';
        byId('collection-status').textContent = 'Не удалось получить данные';
        showError(error);
    } finally {
        setCollectionLoading(false);
    }
}

function renderCollectionError() {
    const body = byId('groups-body');
    body.replaceChildren();
    const row = body.insertRow();
    const cell = row.insertCell();
    cell.colSpan = 8;
    cell.className = 'empty-cell';
    const box = document.createElement('div');
    box.className = 'empty-state';
    const title = document.createElement('strong');
    title.textContent = 'Коллекция временно недоступна';
    const text = document.createElement('span');
    text.textContent = 'Проверьте состояние сервисов и попробуйте ещё раз.';
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.textContent = 'Повторить запрос';
    retry.onclick = loadGroups;
    box.append(title, text, retry);
    cell.append(box);
}

function renderGroups(page) {
    const body = byId('groups-body');
    body.replaceChildren();
    if (!page.content.length) {
        const row = body.insertRow();
        const cell = row.insertCell();
        cell.colSpan = 8;
        cell.className = 'empty-cell';
        const box = document.createElement('div');
        box.className = 'empty-state';
        const title = document.createElement('strong');
        title.textContent = 'Группы не найдены';
        const text = document.createElement('span');
        text.textContent = lines('filter').length ? 'Измените условия фильтрации.' : 'Создайте первую учебную группу.';
        box.append(title, text);
        if (!lines('filter').length) {
            const create = document.createElement('button');
            create.type = 'button';
            create.className = 'primary';
            create.textContent = 'Создать группу';
            create.onclick = () => openEditor();
            box.append(create);
        }
        cell.append(box);
    }
    page.content.forEach(group => {
        const row = body.insertRow();
        addCell(row, group.id);
        const name = addCell(row);
        addStrong(name, group.name);
        appendText(name, group.semesterEnum ? `${readable(group.semesterEnum)} семестр` : 'Семестр не указан', 'subtle');
        addCell(row, `${group.coordinates.x}; ${group.coordinates.y}`);
        addCell(row, group.studentsCount.toLocaleString('ru-RU'));
        const education = addCell(row);
        const educationBadge = document.createElement('span');
        educationBadge.className = 'education-badge';
        educationBadge.textContent = readable(group.formOfEducation);
        education.append(educationBadge);
        const admin = addCell(row);
        if (group.groupAdmin) {
            addStrong(admin, group.groupAdmin.name);
            appendText(admin, [readable(group.groupAdmin.nationality), readable(group.groupAdmin.hairColor)].filter(Boolean).join(' · '), 'subtle');
            if (group.groupAdmin.location) {
                appendText(admin, `Место: ${group.groupAdmin.location.x}; ${group.groupAdmin.location.y}; ${group.groupAdmin.location.z}`, 'subtle');
            }
        } else {
            admin.textContent = 'Не указан';
            admin.classList.add('muted');
        }
        addCell(row, formatDate(group.creationDate));
        const actions = addCell(row);
        actions.className = 'row-actions';
        actions.dataset.label = TABLE_LABELS[7];
        const edit = document.createElement('button');
        edit.type = 'button';
        edit.textContent = 'Изменить';
        edit.setAttribute('aria-label', `Изменить группу ${group.name}`);
        edit.onclick = () => openEditor(group);
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.textContent = 'Удалить';
        remove.className = 'danger';
        remove.setAttribute('aria-label', `Удалить группу ${group.name}`);
        remove.onclick = () => deleteGroup(group);
        actions.append(edit, remove);
    });
    const humanPage = page.totalPages === 0 ? 0 : page.page + 1;
    byId('page-summary').textContent = `Страница ${humanPage} из ${page.totalPages} · всего ${page.totalElements}`;
    updatePager();
}

function addCell(row, text) {
    const cell = row.insertCell();
    const index = row.cells.length - 1;
    cell.dataset.label = TABLE_LABELS[index] || '';
    if (text !== undefined) cell.textContent = text;
    return cell;
}

function addStrong(parent, text) {
    const element = document.createElement('strong');
    element.textContent = text;
    parent.append(element);
}

function appendText(parent, text, className) {
    if (!text) return;
    const element = document.createElement('div');
    element.className = className;
    element.textContent = text;
    parent.append(element);
}

function formatDate(value) {
    return new Intl.DateTimeFormat('ru-RU', {
        day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(new Date(value));
}

function resetEditor() {
    byId('group-form').reset();
    byId('edit-id').value = '';
    byId('dialog-title').textContent = 'Новая группа';
    byId('education-form').value = 'DISTANCE_EDUCATION';
    toggleAdmin();
    toggleLocation();
}

function setBirthdayFields(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value || '');
    byId('birthday-day').value = match ? Number(match[3]) : '';
    byId('birthday-month').value = match ? Number(match[2]) : '';
    byId('birthday-year').value = match ? Number(match[1]) : '';
}

function birthdayValue() {
    const values = ['birthday-day', 'birthday-month', 'birthday-year'].map(id => byId(id).value.trim());
    if (values.every(value => !value)) return null;
    if (values.some(value => !value)) {
        throw new Error('Укажите дату рождения полностью: день, месяц и год.');
    }
    const [day, month, year] = values.map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
        throw new Error('Укажите существующую дату рождения.');
    }
    return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}T00:00:00+03:00[Europe/Moscow]`;
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
        byId('has-admin').checked = Boolean(group.groupAdmin);
        toggleAdmin();
        if (group.groupAdmin) {
            byId('person-name').value = group.groupAdmin.name;
            setBirthdayFields(group.groupAdmin.birthday);
            byId('hair-color').value = group.groupAdmin.hairColor || '';
            byId('nationality').value = group.groupAdmin.nationality;
            byId('has-location').checked = Boolean(group.groupAdmin.location);
            toggleLocation();
            if (group.groupAdmin.location) {
                byId('location-x').value = group.groupAdmin.location.x;
                byId('location-y').value = group.groupAdmin.location.y;
                byId('location-z').value = group.groupAdmin.location.z;
            }
        }
    }
    byId('group-dialog').showModal();
    byId('group-name').focus();
}

function toggleAdmin() {
    const enabled = byId('has-admin').checked;
    byId('admin-fields').disabled = !enabled;
    byId('person-name').required = enabled;
    if (enabled && !byId('nationality').value) byId('nationality').value = 'USA';
}

function toggleLocation() {
    const enabled = byId('has-admin').checked && byId('has-location').checked;
    ['location-x', 'location-y', 'location-z'].forEach(id => {
        byId(id).disabled = !enabled;
        byId(id).required = enabled;
    });
    byId('location-fields').setAttribute('aria-disabled', String(!enabled));
}

function formPayload() {
    const payload = {
        name: byId('group-name').value.trim(),
        coordinates: {
            x: Number(byId('coordinate-x').value),
            y: Number(byId('coordinate-y').value)
        },
        studentsCount: Number(byId('students-count').value),
        formOfEducation: byId('education-form').value
    };
    if (byId('semester').value) payload.semesterEnum = byId('semester').value;
    if (byId('has-admin').checked) {
        payload.groupAdmin = {
            name: byId('person-name').value.trim(),
            nationality: byId('nationality').value
        };
        const birthday = birthdayValue();
        if (birthday) payload.groupAdmin.birthday = birthday;
        if (byId('hair-color').value) payload.groupAdmin.hairColor = byId('hair-color').value;
        if (byId('has-location').checked) {
            payload.groupAdmin.location = {
                x: Number(byId('location-x').value),
                y: Number(byId('location-y').value),
                z: Number(byId('location-z').value)
            };
        }
    }
    return payload;
}

function setButtonBusy(button, busy, busyText) {
    if (busy) {
        button.dataset.label = button.textContent;
        button.textContent = busyText;
        button.disabled = true;
        button.classList.add('button-loading');
    } else {
        button.textContent = button.dataset.label;
        button.disabled = false;
        button.classList.remove('button-loading');
    }
}

async function saveGroup(event) {
    event.preventDefault();
    const id = byId('edit-id').value;
    const submit = byId('group-form').querySelector('button[type="submit"]');
    setButtonBusy(submit, true, 'Сохраняем…');
    try {
        const group = await api(id ? `/api/study-groups/${id}` : '/api/study-groups', {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify(formPayload())
        });
        byId('group-dialog').close();
        showNotice(id ? `Группа #${id} обновлена.` : `Группа #${group.id} создана.`);
        await loadGroups();
    } catch (error) {
        showError(error);
    } finally {
        setButtonBusy(submit, false);
    }
}

async function deleteGroup(group) {
    if (!confirm(`Удалить группу «${group.name}» (#${group.id})?`)) return;
    try {
        await api(`/api/study-groups/${group.id}`, { method: 'DELETE' });
        showNotice(`Группа #${group.id} удалена.`);
        await loadGroups();
    } catch (error) {
        showError(error);
    }
}

function renderSpecial(value) {
    const box = byId('special-result');
    box.classList.remove('muted');
    box.replaceChildren();
    if (Array.isArray(value)) {
        if (!value.length) {
            box.textContent = 'Совпадений нет.';
            return;
        }
        value.forEach(group => appendText(box, `#${group.id} · ${group.name} · ${group.studentsCount} студентов`, 'result-item'));
    } else if (value?.id) {
        box.textContent = `#${value.id} · ${value.name} · администратор: ${value.groupAdmin?.name || 'не указан'}`;
    } else {
        box.textContent = `Количество: ${value.count} · сравнение с «${value.greaterThan}»`;
    }
}

byId('query-form').addEventListener('submit', event => {
    event.preventDefault();
    loadGroups();
});
byId('reset-query').onclick = () => {
    byId('page').value = 0;
    byId('size').value = 10;
    byId('sort').value = 'id,asc';
    byId('filter').value = '';
    loadGroups();
};
byId('previous-page').onclick = () => {
    byId('page').value = Math.max(0, Number(byId('page').value) - 1);
    loadGroups();
};
byId('next-page').onclick = () => {
    byId('page').value = Number(byId('page').value) + 1;
    loadGroups();
};
byId('new-group').onclick = () => openEditor();
byId('close-dialog').onclick = byId('cancel-dialog').onclick = () => byId('group-dialog').close();
byId('group-dialog').addEventListener('close', () => {
    const notice = byId('notice');
    document.querySelector('.app-shell').prepend(notice);
    notice.hidden = true;
});
byId('has-admin').onchange = () => { toggleAdmin(); toggleLocation(); };
byId('has-location').onchange = toggleLocation;
byId('group-form').addEventListener('submit', saveGroup);

byId('get-by-id-form').onsubmit = async event => {
    event.preventDefault();
    try { renderSpecial(await api(`/api/study-groups/${byId('lookup-id').value}`)); }
    catch (error) { showError(error); }
};
byId('max-admin').onclick = async () => {
    try { renderSpecial(await api('/api/study-groups/group-admin/max')); }
    catch (error) { showError(error); }
};
byId('count-admin-form').onsubmit = async event => {
    event.preventDefault();
    try {
        renderSpecial(await api(`/api/study-groups/group-admin/count-greater?adminName=${encodeURIComponent(byId('admin-name').value)}`));
    } catch (error) { showError(error); }
};
byId('contains-form').onsubmit = async event => {
    event.preventDefault();
    try {
        renderSpecial(await api(`/api/study-groups/name/contains?substring=${encodeURIComponent(byId('substring').value)}`));
    } catch (error) { showError(error); }
};
byId('isu-form').onsubmit = async event => {
    event.preventDefault();
    const id = byId('isu-group-id').value;
    const form = byId('isu-form-value').value;
    try {
        const result = await api(`/isu/group/${id}/change-edu-form/${form}`, { method: 'POST' });
        byId('isu-result').textContent = `Группа #${id}: новая форма — ${readable(result.formOfEducation)}.`;
        byId('isu-result').classList.remove('muted');
        await loadGroups();
    } catch (error) { showError(error); }
};
byId('expel-all').onclick = async () => {
    const id = byId('isu-group-id').value;
    if (!id) {
        showNotice('Укажите ID группы для операции ИСУ.', true);
        return;
    }
    if (!confirm(`Отчислить всех студентов и удалить пустую группу #${id}?`)) return;
    try {
        const result = await api(`/isu/group/${id}/expel-all`, { method: 'POST' });
        byId('isu-result').textContent = result.message;
        byId('isu-result').classList.remove('muted');
        await loadGroups();
    } catch (error) { showError(error); }
};

loadGroups();
