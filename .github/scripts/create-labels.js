#!/usr/bin/env node

/**
 * Script Node.js para crear labels automáticamente en GitHub usando la API
 * Requiere: Node.js y token de GitHub (GITHUB_TOKEN)
 * 
 * Uso:
 *   export GITHUB_TOKEN=tu_token
 *   node create-labels.js
 * 
 * O configura el token en .env:
 *   GITHUB_TOKEN=tu_token
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

// Colores para output
const colors = {
    green: '\x1b[32m',
    yellow: '\x1b[33m',
    red: '\x1b[31m',
    cyan: '\x1b[36m',
    reset: '\x1b[0m'
};

function log(message, color = 'reset') {
    console.log(`${colors[color]}${message}${colors.reset}`);
}

// Obtener token de GitHub
const GITHUB_TOKEN = process.env.GITHUB_TOKEN || 
                     (fs.existsSync('.env') && fs.readFileSync('.env', 'utf8')
                      .split('\n').find(line => line.startsWith('GITHUB_TOKEN'))?.split('=')[1]?.trim());

if (!GITHUB_TOKEN) {
    log('❌ Error: GITHUB_TOKEN no encontrado.', 'red');
    log('Configura el token como variable de entorno o en .env', 'cyan');
    log('  export GITHUB_TOKEN=tu_token', 'cyan');
    process.exit(1);
}

// Obtener owner y repo desde git remote o usar valores por defecto
const getRepoInfo = () => {
    try {
        const gitConfig = fs.readFileSync('.git/config', 'utf8');
        const urlMatch = gitConfig.match(/url = .*github\.com[\/:]([^\/]+)\/([^\/]+)\.git/);
        if (urlMatch) {
            return { owner: urlMatch[1], repo: urlMatch[2].replace('.git', '') };
        }
    } catch (e) {}
    
    // Valores por defecto (actualizar si es necesario)
    return { owner: 'Shurdani', repo: 'ZipStats' };
};

const { owner, repo } = getRepoInfo();

// Labels a crear
const labels = [
    { name: '🐛 bug', color: 'FF6B6B', description: 'Algo no funciona correctamente' },
    { name: '✨ feature', color: '51CF66', description: 'Nueva funcionalidad o característica' },
    { name: '🎨 ui', color: '9775FA', description: 'Cambios de interfaz o diseño' },
    { name: '🔧 refactor', color: '339AF0', description: 'Refactorización de código' },
    { name: '📝 documentation', color: 'F59F00', description: 'Cambios en documentación' },
    { name: '🧪 tests', color: '37B24D', description: 'Tests o mejoras de testing' },
    { name: '🔒 security', color: 'E03131', description: 'Cambios relacionados con seguridad' },
    { name: '⚙️ config', color: '868E96', description: 'Cambios de configuración' },
    { name: '🚀 release', color: 'FA5252', description: 'Preparación de release o versión' },
    { name: '🔨 maintenance', color: '495057', description: 'Tareas de mantenimiento' },
    { name: '⚡ performance', color: '845EF7', description: 'Mejoras de rendimiento' },
    { name: '🐛 bugfix', color: 'FF6B6B', description: 'Corrección de errores' },
    { name: '📦 dependencies', color: '845EF7', description: 'Actualización de dependencias' }
];

// Función para hacer request a GitHub API
function githubRequest(method, endpoint, data = null) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: 'api.github.com',
            port: 443,
            path: `/repos/${owner}/${repo}${endpoint}`,
            method: method,
            headers: {
                'Authorization': `token ${GITHUB_TOKEN}`,
                'User-Agent': 'Node.js Label Creator',
                'Accept': 'application/vnd.github.v3+json',
                'Content-Type': 'application/json'
            }
        };

        if (data) {
            const jsonData = JSON.stringify(data);
            options.headers['Content-Length'] = Buffer.byteLength(jsonData);
        }

        const req = https.request(options, (res) => {
            let body = '';
            res.on('data', (chunk) => body += chunk);
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    resolve(JSON.parse(body || '{}'));
                } else if (res.statusCode === 422) {
                    // Label ya existe
                    resolve(null);
                } else {
                    reject(new Error(`HTTP ${res.statusCode}: ${body}`));
                }
            });
        });

        req.on('error', reject);
        if (data) {
            req.write(JSON.stringify(data));
        }
        req.end();
    });
}

// Obtener labels existentes
async function getExistingLabels() {
    try {
        const labels = await githubRequest('GET', '/labels?per_page=100');
        return labels.map(l => l.name);
    } catch (error) {
        log(`⚠️  Error obteniendo labels existentes: ${error.message}`, 'yellow');
        return [];
    }
}

// Crear label
async function createLabel(label) {
    try {
        await githubRequest('POST', '/labels', {
            name: label.name,
            color: label.color,
            description: label.description
        });
        return true;
    } catch (error) {
        if (error.message.includes('422')) {
            return null; // Ya existe
        }
        throw error;
    }
}

// Función principal
async function main() {
    log('🏷️  Creando labels en GitHub...', 'green');
    log(`Repository: ${owner}/${repo}`, 'cyan');
    log('');

    const existingLabels = await getExistingLabels();
    let created = 0;
    let skipped = 0;
    let failed = 0;

    for (const label of labels) {
        if (existingLabels.includes(label.name)) {
            log(`⏭️  Label '${label.name}' ya existe, saltando...`, 'yellow');
            skipped++;
        } else {
            try {
                const result = await createLabel(label);
                if (result) {
                    log(`✅ Creada: ${label.name}`, 'green');
                    created++;
                } else {
                    log(`⏭️  Label '${label.name}' ya existe, saltando...`, 'yellow');
                    skipped++;
                }
            } catch (error) {
                log(`❌ Error creando '${label.name}': ${error.message}`, 'red');
                failed++;
            }
        }
    }

    // Resumen
    log('');
    log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━', 'green');
    log(`✅ Creadas: ${created}`, 'green');
    log(`⏭️  Saltadas: ${skipped}`, 'yellow');
    if (failed > 0) {
        log(`❌ Fallidas: ${failed}`, 'red');
    }
    log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━', 'green');
    log('');

    if (failed === 0) {
        log('🎉 ¡Labels creadas exitosamente!', 'green');
        process.exit(0);
    } else {
        log('⚠️  Algunas labels fallaron. Revisa los errores arriba.', 'red');
        process.exit(1);
    }
}

main().catch(error => {
    log(`❌ Error fatal: ${error.message}`, 'red');
    process.exit(1);
});

