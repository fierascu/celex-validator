import { test, expect, Page } from '@playwright/test';

// ── Helpers ────────────────────────────────────────────────────────────────

/** Type a CELEX number and wait for the status badge to appear. */
async function validate(page: Page, celex: string) {
  await page.fill('#celexInput', celex);
  await page.click('#validateBtn');
}

/** Wait until the status area no longer shows the "Checking…" spinner. */
async function waitForResult(page: Page) {
  await expect(page.locator('#statusArea .status-badge.idle')).toHaveCount(0);
}

// ── Page-load behaviour ────────────────────────────────────────────────────

test.describe('Page load', () => {
  test('input receives focus automatically on page load', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#celexInput')).toBeFocused();
  });

  test('status area has aria-live="polite"', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#statusArea')).toHaveAttribute('aria-live', 'polite');
  });

  test('error area has role="alert"', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#errorArea')).toHaveAttribute('role', 'alert');
  });

  test('page title is visible', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h1')).toHaveText('CELEX Validator');
  });
});

// ── Tab-order ──────────────────────────────────────────────────────────────

test.describe('Tab order', () => {
  test('Tab moves focus: input → Validate → Clear', async ({ page }) => {
    await page.goto('/');
    // Input already has focus on load
    await expect(page.locator('#celexInput')).toBeFocused();

    await page.keyboard.press('Tab');
    await expect(page.locator('#validateBtn')).toBeFocused();

    await page.keyboard.press('Tab');
    await expect(page.locator('#clearBtn')).toBeFocused();
  });

  test('Tab reaches the "Try an example" toggle', async ({ page }) => {
    await page.goto('/');
    // advance past input, Validate, Clear
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await expect(page.locator('#examplesToggle')).toBeFocused();
  });

  test('Shift+Tab moves focus backwards from Validate to input', async ({ page }) => {
    await page.goto('/');
    await page.focus('#validateBtn');
    await page.keyboard.press('Shift+Tab');
    await expect(page.locator('#celexInput')).toBeFocused();
  });
});

// ── Keyboard activation ────────────────────────────────────────────────────

test.describe('Keyboard activation', () => {
  test('Enter in the input field triggers validation', async ({ page }) => {
    await page.goto('/');
    await page.fill('#celexInput', '32006L0112');
    await page.keyboard.press('Enter');
    await expect(page.locator('#statusArea')).toContainText('Valid syntax');
  });

  test('Space on Validate button triggers validation', async ({ page }) => {
    await page.goto('/');
    await page.fill('#celexInput', '32016R0679');
    await page.focus('#validateBtn');
    await page.keyboard.press('Space');
    await expect(page.locator('#statusArea')).toContainText('Valid syntax');
  });

  test('Enter on Validate button triggers validation', async ({ page }) => {
    await page.goto('/');
    await page.fill('#celexInput', '62019CJ0001');
    await page.focus('#validateBtn');
    await page.keyboard.press('Enter');
    await expect(page.locator('#statusArea')).toContainText('Valid syntax');
  });

  test('Enter on Clear button resets the form', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('#statusArea')).toContainText('Valid syntax');

    await page.focus('#clearBtn');
    await page.keyboard.press('Enter');
    await expect(page.locator('#celexInput')).toHaveValue('');
    await expect(page.locator('#statusArea')).toBeEmpty();
  });
});

// ── Example chips ──────────────────────────────────────────────────────────

test.describe('Example chips', () => {
  test('"Try an example" toggle expands the chip list on click', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#examplesList')).not.toHaveClass(/open/);
    await page.click('#examplesToggle');
    await expect(page.locator('#examplesToggle')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#examplesList')).toHaveClass(/open/);
  });

  test('"Try an example" toggle collapses on second click', async ({ page }) => {
    await page.goto('/');
    await page.click('#examplesToggle');
    await page.click('#examplesToggle');
    await expect(page.locator('#examplesToggle')).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('#examplesList')).not.toHaveClass(/open/);
  });

  test('"Try an example" toggle activates with keyboard Enter', async ({ page }) => {
    await page.goto('/');
    await page.focus('#examplesToggle');
    await page.keyboard.press('Enter');
    await expect(page.locator('#examplesToggle')).toHaveAttribute('aria-expanded', 'true');
  });

  test('"Try an example" toggle activates with keyboard Space', async ({ page }) => {
    await page.goto('/');
    await page.focus('#examplesToggle');
    await page.keyboard.press('Space');
    await expect(page.locator('#examplesToggle')).toHaveAttribute('aria-expanded', 'true');
  });

  test('clicking an example chip fills the input and validates', async ({ page }) => {
    await page.goto('/');
    await page.click('#examplesToggle');
    const chip = page.locator('.example-chip').first();
    const chipText = await chip.textContent();
    await chip.click();
    await expect(page.locator('#celexInput')).toHaveValue(chipText!.trim());
    await expect(page.locator('#statusArea')).toContainText('Valid syntax');
  });

  test('example chip is keyboard-activatable with Enter', async ({ page }) => {
    await page.goto('/');
    await page.click('#examplesToggle');
    const chip = page.locator('.example-chip').first();
    await chip.focus();
    await page.keyboard.press('Enter');
    await expect(page.locator('#statusArea')).toContainText('Valid syntax');
  });

  test('example chip is keyboard-activatable with Space', async ({ page }) => {
    await page.goto('/');
    await page.click('#examplesToggle');
    const chip = page.locator('.example-chip').first();
    await chip.focus();
    await page.keyboard.press('Space');
    await expect(page.locator('#statusArea')).toContainText('Valid syntax');
  });
});

// ── Validation results ─────────────────────────────────────────────────────

test.describe('Validation results', () => {
  test('valid CELEX shows "Valid syntax" badge', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('.status-badge.valid')).toBeVisible();
    await expect(page.locator('.status-badge.valid')).toContainText('Valid syntax');
  });

  test('invalid CELEX shows "Invalid" badge', async ({ page }) => {
    await page.goto('/');
    await validate(page, 'NOTACELEX');
    await expect(page.locator('.status-badge.invalid')).toBeVisible();
    await expect(page.locator('.status-badge.invalid')).toContainText('Invalid');
  });

  test('invalid CELEX sets aria-invalid="true" on input', async ({ page }) => {
    await page.goto('/');
    await validate(page, 'NOTACELEX');
    await expect(page.locator('#celexInput')).toHaveAttribute('aria-invalid', 'true');
  });

  test('valid CELEX removes aria-invalid from input', async ({ page }) => {
    await page.goto('/');
    // first make it invalid …
    await validate(page, 'NOTACELEX');
    await expect(page.locator('#celexInput')).toHaveAttribute('aria-invalid', 'true');
    // … then fix it
    await validate(page, '32006L0112');
    await expect(page.locator('#celexInput')).not.toHaveAttribute('aria-invalid');
  });

  test('invalid CELEX shows error message', async ({ page }) => {
    await page.goto('/');
    await validate(page, 'NOTACELEX');
    await expect(page.locator('#errorArea .error-msg')).toBeVisible();
    await expect(page.locator('#errorArea .error-msg')).not.toBeEmpty();
  });

  test('valid CELEX renders the visual segment display', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('.celex-display')).toBeVisible();
    await expect(page.locator('.celex-segment')).toHaveCount(4); // sector, year, doctype, number
  });

  test('valid CELEX shows the "Details" toggle button', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('#detailsToggleWrap')).not.toHaveAttribute('hidden');
    await expect(page.locator('#detailsToggle')).toBeVisible();
  });
});

// ── Details panel ──────────────────────────────────────────────────────────

test.describe('Details panel', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('#detailsToggleWrap')).not.toHaveAttribute('hidden');
  });

  test('Details panel is collapsed by default', async ({ page }) => {
    await expect(page.locator('#detailsToggle')).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('#detailsPanel')).not.toHaveClass(/open/);
  });

  test('clicking Details toggle expands the panel', async ({ page }) => {
    await page.click('#detailsToggle');
    await expect(page.locator('#detailsToggle')).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#detailsPanel')).toHaveClass(/open/);
  });

  test('clicking Details toggle a second time collapses the panel', async ({ page }) => {
    await page.click('#detailsToggle');
    await page.click('#detailsToggle');
    await expect(page.locator('#detailsToggle')).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('#detailsPanel')).not.toHaveClass(/open/);
  });

  test('Details toggle expands with keyboard Enter', async ({ page }) => {
    await page.focus('#detailsToggle');
    await page.keyboard.press('Enter');
    await expect(page.locator('#detailsToggle')).toHaveAttribute('aria-expanded', 'true');
  });

  test('Details toggle expands with keyboard Space', async ({ page }) => {
    await page.focus('#detailsToggle');
    await page.keyboard.press('Space');
    await expect(page.locator('#detailsToggle')).toHaveAttribute('aria-expanded', 'true');
  });

  test('expanded panel contains the result table', async ({ page }) => {
    await page.click('#detailsToggle');
    await expect(page.locator('#detailsPanel .result-table')).toBeVisible();
    // table must have at least the Input row
    await expect(page.locator('#detailsPanel .result-table th[scope="row"]').first())
      .toHaveText('Input');
  });

  test('details panel has role="region" and accessible label', async ({ page }) => {
    await expect(page.locator('#detailsPanel')).toHaveAttribute('role', 'region');
    await expect(page.locator('#detailsPanel')).toHaveAttribute('aria-label', 'Parsed CELEX details');
  });
});

// ── Clear button ───────────────────────────────────────────────────────────

test.describe('Clear button', () => {
  test('Clear resets input value', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await page.click('#clearBtn');
    await expect(page.locator('#celexInput')).toHaveValue('');
  });

  test('Clear removes status badge', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await page.click('#clearBtn');
    await expect(page.locator('#statusArea')).toBeEmpty();
  });

  test('Clear removes error message', async ({ page }) => {
    await page.goto('/');
    await validate(page, 'NOTACELEX');
    await page.click('#clearBtn');
    await expect(page.locator('#errorArea')).toBeEmpty();
  });

  test('Clear hides the visual segment display', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await page.click('#clearBtn');
    await expect(page.locator('.celex-display')).not.toBeVisible();
  });

  test('Clear hides the Details toggle', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('#detailsToggleWrap')).not.toHaveAttribute('hidden');
    await page.click('#clearBtn');
    await expect(page.locator('#detailsToggleWrap')).toHaveAttribute('hidden', '');
  });

  test('Clear removes aria-invalid from input', async ({ page }) => {
    await page.goto('/');
    await validate(page, 'NOTACELEX');
    await expect(page.locator('#celexInput')).toHaveAttribute('aria-invalid', 'true');
    await page.click('#clearBtn');
    await expect(page.locator('#celexInput')).not.toHaveAttribute('aria-invalid');
  });
});

// ── Live debounce (type-as-you-go) ────────────────────────────────────────

test.describe('Debounced live validation', () => {
  test('typing a valid CELEX (≥5 chars) eventually shows a result', async ({ page }) => {
    await page.goto('/');
    // type character by character so the debounce fires naturally
    await page.locator('#celexInput').pressSequentially('32006L0112', { delay: 30 });
    // wait up to 2 s for the debounce + network round-trip
    await expect(page.locator('.status-badge.valid')).toBeVisible({ timeout: 2_000 });
  });

  test('fewer than 5 typed characters do not trigger automatic validation', async ({ page }) => {
    await page.goto('/');
    await page.locator('#celexInput').pressSequentially('3200', { delay: 30 });
    // wait longer than the 450 ms debounce to be sure nothing fired
    await page.waitForTimeout(600);
    await expect(page.locator('#statusArea')).toBeEmpty();
  });
});

// ── ARIA attributes ────────────────────────────────────────────────────────

test.describe('ARIA attributes', () => {
  test('examples toggle has aria-controls pointing at the list', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#examplesToggle')).toHaveAttribute('aria-controls', 'examplesList');
  });

  test('examples list has role="group"', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#examplesList')).toHaveAttribute('role', 'group');
  });

  test('details toggle has aria-controls pointing at the panel', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('#detailsToggle')).toHaveAttribute('aria-controls', 'detailsPanel');
  });

  test('visual breakdown has role="group" with accessible label', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await expect(page.locator('.celex-display')).toHaveAttribute('role', 'group');
    await expect(page.locator('.celex-display')).toHaveAttribute('aria-label', 'CELEX number breakdown');
  });

  test('result table has a caption', async ({ page }) => {
    await page.goto('/');
    await validate(page, '32006L0112');
    await page.click('#detailsToggle');
    await expect(page.locator('.result-table caption')).toHaveText('Parsed CELEX components');
  });
});

// ── Reference section ──────────────────────────────────────────────────────

test.describe('Reference codes section', () => {
  test('<details> summary is keyboard-activatable and opens the section', async ({ page }) => {
    await page.goto('/');
    await page.focus('#refDetails summary');
    await page.keyboard.press('Enter');
    await expect(page.locator('#refDetails')).toHaveAttribute('open', '');
  });

  test('sector grid loads after opening the reference section', async ({ page }) => {
    await page.goto('/');
    await page.click('#refDetails summary');
    await expect(page.locator('#sectorGrid .ref-item')).not.toHaveCount(0, { timeout: 5_000 });
  });

  test('sector filter dropdown is focusable', async ({ page }) => {
    await page.goto('/');
    await page.click('#refDetails summary');
    await page.focus('#sectorFilter');
    await expect(page.locator('#sectorFilter')).toBeFocused();
  });
});

// ── Focus-visible ring (structural) ───────────────────────────────────────

test.describe('Focus-visible styles (structural)', () => {
  test(':focus-visible style rule is declared in the page', async ({ page }) => {
    await page.goto('/');
    const hasFocusVisible = await page.evaluate(() => {
      return [...document.styleSheets].some(sheet => {
        try {
          return [...(sheet.cssRules ?? [])].some(
            rule => rule instanceof CSSStyleRule && rule.selectorText === ':focus-visible'
          );
        } catch {
          return false;
        }
      });
    });
    expect(hasFocusVisible).toBe(true);
  });
});
