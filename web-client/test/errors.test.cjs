const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Проверяем сетевой слой и уведомления без загрузки таблицы и форм приложения.
const source = readFileSync(path.join(__dirname, '../public/app.js'), 'utf8').split('\nfunction lines(')[0];

function client(fetch, config = {}) {
    const elements = new Map();
    const context = vm.createContext({
        APP_CONFIG: config,
        fetch, Option: function () {}, setTimeout: () => 0, clearTimeout: () => {},
        document: { getElementById(id) {
            if (!elements.has(id)) elements.set(id, {
                add() {}, classList: { toggle() {} }, setAttribute() {}, open: false
            });
            return elements.get(id);
        } }
    });
    vm.runInContext(source, context);
    return { api: context.api, showError: context.showError, elements };
}

test('сетевые ошибки переведены на русский', async () => {
    const app = client(async () => { throw new TypeError('Failed to fetch'); });
    await assert.rejects(app.api('/api/study-groups'), /Не удалось связаться с сервером/);
});

test('ошибки чтения ответа переведены на русский', async () => {
    const app = client(async () => ({ text() { throw new Error('Connection reset'); } }));
    await assert.rejects(app.api('/api/study-groups'), /Не удалось связаться с сервером/);
});

test('HTML-ошибка прокси получает понятное сообщение', async () => {
    const app = client(async () => new Response('<h1>Bad Gateway</h1>', { status: 502 }));
    await assert.rejects(app.api('/api/study-groups'), /Сервис временно недоступен/);
});

test('неверный успешный ответ не доходит до отображения таблицы', async () => {
    const app = client(async () => new Response('{invalid', { headers: { 'Content-Type': 'application/json' } }));
    await assert.rejects(app.api('/api/study-groups'), /Сервер вернул некорректный ответ/);
});

test('успешное удаление с пустым ответом допустимо', async () => {
    const app = client(async () => new Response(null, { status: 204 }));
    assert.equal(await app.api('/api/study-groups/1', { method: 'DELETE' }), null);
});

test('ошибки валидации сохраняют код и показывают русские названия полей', async () => {
    const app = client(async () => Response.json({
        code: 'VALIDATION_FAILED', message: 'Поля запроса не прошли проверку',
        violations: { studentsCount: 'Значение должно быть больше нуля' }
    }, { status: 422 }));
    await assert.rejects(app.api('/api/study-groups'), error => {
        assert.equal(error.status, 422);
        assert.equal(error.body.code, 'VALIDATION_FAILED');
        app.showError(error);
        assert.equal(app.elements.get('notice').textContent,
            'Поля запроса не прошли проверку — Количество студентов: Значение должно быть больше нуля');
        return true;
    });
});

test('технические имена параметров и типов имеют русские подписи', () => {
    const app = client();
    app.showError({ message: 'Параметр имеет неверный тип',
        body: { details: { parameter: 'groupId', expectedType: 'int' } } });
    assert.equal(app.elements.get('notice').textContent,
        'Параметр имеет неверный тип — Параметр: Идентификатор группы; Ожидаемый тип: целое число');
});


test('Config Server не принимается за успешный ответ API', async () => {
    const app = client(async () => Response.json({ name: 'api', profiles: ['study-groups'], propertySources: [] }));
    await assert.rejects(app.api('/api/study-groups'), /Config Server вместо API групп/);
});

test('статический клиент обращается к API через настроенный PHP-прокси', async () => {
    const requests = [];
    const app = client(async url => { requests.push(url); return Response.json({ id: 1 }); }, { apiBase: 'proxy.php/' });
    await app.api('/api/study-groups/1');
    await app.api('/isu/group/1/expel-all', { method: 'POST' });
    assert.deepEqual(requests, ['proxy.php/api/study-groups/1', 'proxy.php/isu/group/1/expel-all']);
});
