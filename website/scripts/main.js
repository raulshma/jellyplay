/**
 * JellyPlay Landing Page — Main Script
 * Scroll animations, screenshot carousel, mobile nav, smooth scrolling, and interactive simulators.
 */

(function () {
  'use strict';

  // Theming lives in scripts/themes.js (app design-system port).
  // This file handles page interactions only.

  // ============ SCROLL ANIMATIONS (IntersectionObserver) ============
  function initScrollAnimations() {
    const elements = document.querySelectorAll('.animate-on-scroll');
    if (!elements.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            // Add stagger delay based on sibling index within parent
            const siblings = entry.target.parentElement.querySelectorAll('.animate-on-scroll');
            let siblingIndex = 0;
            siblings.forEach((sib, i) => {
              if (sib === entry.target) siblingIndex = i;
            });

            entry.target.style.transitionDelay = `${siblingIndex * 0.08}s`;
            entry.target.classList.add('visible');
            observer.unobserve(entry.target);
          }
        });
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -60px 0px',
      }
    );

    elements.forEach((el) => observer.observe(el));
  }

  // ============ MOBILE NAVIGATION ============
  function initMobileNav() {
    const toggle = document.getElementById('nav-toggle');
    const menu = document.getElementById('mobile-menu');
    const links = document.querySelectorAll('.mobile-link, .mobile-menu a, .nav-logo');

    if (!toggle || !menu) return;

    toggle.addEventListener('click', () => {
      const isOpen = menu.classList.toggle('active');
      const icon = toggle.querySelector('md-icon');
      if (icon) {
        icon.textContent = isOpen ? 'close' : 'menu';
      }
      document.body.style.overflow = isOpen ? 'hidden' : '';
    });

    links.forEach((link) => {
      link.addEventListener('click', () => {
        menu.classList.remove('active');
        const icon = toggle.querySelector('md-icon');
        if (icon) icon.textContent = 'menu';
        document.body.style.overflow = '';
      });
    });
  }

  // ============ NAVBAR SCROLL EFFECT ============
  function initNavScrollEffect() {
    const nav = document.getElementById('site-nav');
    if (!nav) return;

    let ticking = false;

    window.addEventListener('scroll', () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          if (window.scrollY > 20) {
            nav.classList.add('scrolled');
          } else {
            nav.classList.remove('scrolled');
          }
          ticking = false;
        });
        ticking = true;
      }
    });
  }

  // ============ SCROLLSPY (active nav link) ============
  function initScrollSpy() {
    const navAnchors = document.querySelectorAll('.nav-links ul a[href^="#"]');
    if (!navAnchors.length) return;

    const sectionToLink = new Map();
    navAnchors.forEach((anchor) => {
      const section = document.querySelector(anchor.getAttribute('href'));
      if (section) sectionToLink.set(section, anchor);
    });
    if (!sectionToLink.size) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            navAnchors.forEach((a) => a.classList.remove('active'));
            const link = sectionToLink.get(entry.target);
            if (link) link.classList.add('active');
          }
        });
      },
      { rootMargin: '-40% 0px -55% 0px' }
    );

    sectionToLink.forEach((_, section) => observer.observe(section));
  }

  // ============ BACK TO TOP ============
  function initBackToTop() {
    const btn = document.getElementById('back-to-top');
    if (!btn) return;

    let ticking = false;
    window.addEventListener('scroll', () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          btn.classList.toggle('visible', window.scrollY > 600);
          ticking = false;
        });
        ticking = true;
      }
    }, { passive: true });

    btn.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  // ============ SCREENSHOT SHOWCASE (stage + filmstrip) ============
  function initShowcase() {
    const stage = document.getElementById('showcase-stage');
    const frame = document.getElementById('showcase-frame');
    const image = document.getElementById('showcase-image');
    const imageNext = document.getElementById('showcase-image-next');
    const titleEl = document.getElementById('showcase-title');
    const descEl = document.getElementById('showcase-desc');
    const counterEl = document.getElementById('showcase-counter');
    const strip = document.getElementById('showcase-filmstrip');
    const prevBtn = document.getElementById('showcase-prev');
    const nextBtn = document.getElementById('showcase-next');
    const autoplayBtn = document.getElementById('showcase-autoplay');
    const autoplayIcon = document.getElementById('showcase-autoplay-icon');
    if (!stage || !frame || !image || !imageNext || !strip) return;

    const thumbs = Array.from(strip.querySelectorAll('.showcase-thumb'));
    const total = thumbs.length;
    if (!total) return;

    // Read each thumb's data attributes once — render/showScreenshot/morphTo
    // all share this Shot list instead of re-reading dataset per call site.
    const shots = thumbs.map((thumb) => ({
      full: thumb.dataset.full,
      alt: thumb.dataset.alt || thumb.dataset.title || '',
      title: thumb.dataset.title || '',
      desc: thumb.dataset.desc || '',
      landscape: thumb.dataset.landscape === 'true',
    }));

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let index = 0;
    let playing = !reduceMotion;
    let timer = null;
    let fadeTimer = null;
    let promoteTimer = null;
    let transitionId = 0;
    const intervalTime = 4000;

    const twoDigits = (n) => String(n).padStart(2, '0');

    // Shared preload-then-commit behind swapImage/morphTo: resolves on load,
    // on error, or on a timeout fallback so a hung image never stalls.
    function preloadImage(src, commit, timeoutMs) {
      const pre = new Image();
      pre.onload = commit;
      pre.onerror = commit;
      pre.src = src;
      clearTimeout(fadeTimer);
      fadeTimer = setTimeout(commit, timeoutMs);
    }

    function render(i) {
      const shot = shots[i];
      titleEl.textContent = shot.title;
      descEl.textContent = shot.desc;
      counterEl.textContent = `${twoDigits(i + 1)} / ${twoDigits(total)}`;
      thumbs.forEach((t, j) => {
        const active = j === i;
        t.classList.toggle('active', active);
        t.setAttribute('aria-current', active ? 'true' : 'false');
      });
      const activeThumb = thumbs[i];
      if (activeThumb) {
        ensureThumbVisible(activeThumb);
      }
    }

    // Scroll the filmstrip itself only — never the page. scrollIntoView()
    // would also scroll every scrollable ancestor, yanking the viewport
    // (and the user's perceived focus) back to the gallery on each tick.
    function ensureThumbVisible(thumb) {
      const stripRect = strip.getBoundingClientRect();
      const thumbRect = thumb.getBoundingClientRect();
      if (thumbRect.left < stripRect.left) {
        strip.scrollBy({ left: thumbRect.left - stripRect.left - 8, behavior: 'smooth' });
      } else if (thumbRect.right > stripRect.right) {
        strip.scrollBy({ left: thumbRect.right - stripRect.right + 8, behavior: 'smooth' });
      }
    }

    function swapImage(src, alt) {
      if (image.getAttribute('src') === src && imageNext.getAttribute('src') !== src) {
        image.alt = alt;
        return;
      }
      if (imageNext.getAttribute('src') === src) return;
      // Preload, then dissolve the incoming shot in over the outgoing one.
      // A generation counter supersedes stale loads when the user flips
      // through shots faster than images arrive.
      const id = ++transitionId;
      const commit = () => {
        if (id !== transitionId) return;
        clearTimeout(fadeTimer);
        imageNext.classList.remove('visible');
        void imageNext.offsetWidth;
        imageNext.src = src;
        imageNext.alt = alt;
        imageNext.classList.add('visible');
        clearTimeout(promoteTimer);
        promoteTimer = setTimeout(() => {
          if (id !== transitionId) return;
          image.src = src;
          image.alt = alt;
          imageNext.classList.remove('visible');
          imageNext.removeAttribute('src');
        }, 600);
      };
      preloadImage(src, commit, 1200);
    }

    function showScreenshot(i) {
      index = (i + total) % total;
      const shot = shots[index];
      render(index);
      // Same aspect: layered cross-dissolve. Aspect change: dip-to-black
      // morph — crossfading across aspects would stretch/crop mid-flight.
      if (frame.classList.contains('is-landscape') !== shot.landscape) {
        morphTo(shot);
      } else {
        swapImage(shot.full, shot.alt);
      }
    }

    // Fade the frame out, swap size + image while invisible, fade back in.
    function morphTo(shot) {
      const id = ++transitionId;
      const src = shot.full;
      const alt = shot.alt;
      const landscape = shot.landscape;
      clearTimeout(promoteTimer);
      frame.classList.add('is-switching');
      let settled = false;
      const commit = () => {
        if (settled || id !== transitionId) return;
        settled = true;
        clearTimeout(fadeTimer);
        frame.classList.toggle('is-landscape', landscape);
        image.src = src;
        image.alt = alt;
        imageNext.classList.remove('visible');
        imageNext.removeAttribute('src');
        requestAnimationFrame(() => requestAnimationFrame(() => {
          if (id !== transitionId) return;
          frame.classList.remove('is-switching');
        }));
      };
      // Worst case: never trap the gallery invisible on a hung image.
      preloadImage(src, commit, 600);
    }

    function startAutoplay() {
      if (reduceMotion || timer) return;
      timer = setInterval(() => showScreenshot(index + 1), intervalTime);
    }

    function stopAutoplay() {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    }

    function setPlaying(next) {
      playing = next;
      updateTimer();
      if (autoplayIcon) autoplayIcon.textContent = playing ? 'pause' : 'play_arrow';
      if (autoplayBtn) {
        autoplayBtn.setAttribute('aria-pressed', playing ? 'true' : 'false');
        autoplayBtn.setAttribute('aria-label', playing ? 'Pause slideshow' : 'Play slideshow');
      }
    }

    // The timer runs only while the gallery is on screen and the user is
    // not interacting with it — background ticks must never steal scroll.
    let isHovering = false;
    let isVisible = true;

    function updateTimer() {
      stopAutoplay();
      if (playing && isVisible && !isHovering) startAutoplay();
    }

    // Pause while the user is interacting; resume after.
    stage.addEventListener('mouseenter', () => { isHovering = true; updateTimer(); });
    stage.addEventListener('mouseleave', () => { isHovering = false; updateTimer(); });
    stage.addEventListener('focusin', () => { isHovering = true; updateTimer(); });
    stage.addEventListener('focusout', () => { isHovering = false; updateTimer(); });
    stage.addEventListener('touchstart', () => { isHovering = true; updateTimer(); }, { passive: true });
    stage.addEventListener('touchend', () => { isHovering = false; updateTimer(); }, { passive: true });

    if ('IntersectionObserver' in window) {
      new IntersectionObserver(
        (entries) => {
          isVisible = entries.some((e) => e.isIntersecting);
          updateTimer();
        },
        { threshold: 0.15 }
      ).observe(stage);
    }

    if (prevBtn) prevBtn.addEventListener('click', () => showScreenshot(index - 1));
    if (nextBtn) nextBtn.addEventListener('click', () => showScreenshot(index + 1));
    if (autoplayBtn) autoplayBtn.addEventListener('click', () => setPlaying(!playing));

    thumbs.forEach((thumb, i) => {
      thumb.addEventListener('click', () => showScreenshot(i));
    });

    stage.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight') {
        e.preventDefault();
        showScreenshot(index + 1);
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        showScreenshot(index - 1);
      }
    });

    render(0);
    setPlaying(playing);
  }

  // ============ FAQ ACCORDIONS ============
  function initFAQs() {
    const faqItems = document.querySelectorAll('.faq-item');
    faqItems.forEach((item) => {
      const trigger = item.querySelector('.faq-trigger');
      const content = item.querySelector('.faq-content');

      if (!trigger || !content) return;

      trigger.addEventListener('click', () => {
        const isActive = item.classList.contains('active');

        // Close all other FAQs
        faqItems.forEach((otherItem) => {
          if (otherItem !== item && otherItem.classList.contains('active')) {
            otherItem.classList.remove('active');
            otherItem.querySelector('.faq-trigger').setAttribute('aria-expanded', 'false');
            otherItem.querySelector('.faq-content').style.maxHeight = '0px';
          }
        });

        // Toggle active item
        if (isActive) {
          item.classList.remove('active');
          trigger.setAttribute('aria-expanded', 'false');
          content.style.maxHeight = '0px';
        } else {
          item.classList.add('active');
          trigger.setAttribute('aria-expanded', 'true');
          content.style.maxHeight = content.scrollHeight + 'px';
        }
      });
    });
  }

  // ============ SMOOTH SCROLL FOR ANCHOR LINKS ============
  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
      anchor.addEventListener('click', (e) => {
        const targetId = anchor.getAttribute('href');
        if (targetId === '#') return;

        const target = document.querySelector(targetId);
        if (target) {
          e.preventDefault();
          const navHeight = 64;
          const targetPosition = target.getBoundingClientRect().top + window.scrollY - navHeight;

          window.scrollTo({
            top: targetPosition,
            behavior: 'smooth',
          });
        }
      });
    });
  }

  // ============ PARALLAX HERO GLOW ============
  function initHeroParallax() {
    const heroGlow = document.querySelector('.hero-glow');
    if (!heroGlow || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    window.addEventListener('mousemove', (e) => {
      const x = (e.clientX / window.innerWidth - 0.5) * 20;
      const y = (e.clientY / window.innerHeight - 0.5) * 20;
      heroGlow.style.transform = `translate(${x}px, ${y}px)`;
    });
  }

  // ============ GITHUB STARS ============
  async function initGithubStars() {
    const starsCountEl = document.getElementById('github-stars-count');
    if (!starsCountEl) return;
    try {
      const response = await fetch('https://api.github.com/repos/raulshma/JellyPlay');
      if (response.ok) {
        const data = await response.json();
        const stars = data.stargazers_count;
        if (stars !== undefined) {
          starsCountEl.textContent = stars;
        }
      }
    } catch (e) {
      console.error('Failed to fetch GitHub stars', e);
    }
  }

  // ============ DIRECT RELEASE DOWNLOADS & MODAL ============
  async function initReleaseAndDownloads() {
    const navBadge = document.getElementById('nav-release-badge');
    const navVersionEl = document.getElementById('nav-release-version');
    const modalVersionEl = document.getElementById('modal-release-version');
    const phoneList = document.getElementById('phone-download-list');
    const tvList = document.getElementById('tv-download-list');
    const desktopList = document.getElementById('desktop-download-list');
    const heroDownloadLeft = document.getElementById('hero-download-btn-left');
    
    // Dropdown selectors
    const dropdownList = document.getElementById('dropdown-list');
    const dropdownToggleBtn = document.getElementById('hero-download-btn-right');
    const dropdownMenu = document.getElementById('download-dropdown-menu');
    
    const modal = document.getElementById('download-modal');
    const closeBtn = document.getElementById('modal-close-btn');
    const triggerBtns = document.querySelectorAll('.trigger-download-modal');
    
    if (!navBadge || !navVersionEl || !modalVersionEl || !phoneList || !tvList || !modal) return;

    // --- 1. Dropdown Toggle and Document Clicks ---
    if (dropdownToggleBtn && dropdownMenu) {
      dropdownToggleBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        const expanded = dropdownToggleBtn.getAttribute('aria-expanded') === 'true';
        dropdownToggleBtn.setAttribute('aria-expanded', !expanded);
        dropdownMenu.classList.toggle('active', !expanded);
      });
      
      // Close dropdown when clicking outside
      document.addEventListener('click', (e) => {
        if (!dropdownMenu.contains(e.target) && e.target !== dropdownToggleBtn) {
          dropdownToggleBtn.setAttribute('aria-expanded', 'false');
          dropdownMenu.classList.remove('active');
        }
      });

      // Prevent closing when clicking inside the dropdown header or sections
      dropdownMenu.addEventListener('click', (e) => {
        if (!e.target.closest('.download-item-btn') && !e.target.closest('.download-item-btn span')) {
          e.stopPropagation();
        }
      });
    }

    // --- 2. Modal open/close listeners (Attach synchronously at startup) ---
    function openModal() {
      modal.classList.add('active');
      document.body.style.overflow = 'hidden';
    }

    function closeModal() {
      modal.classList.remove('active');
      document.body.style.overflow = '';
    }

    triggerBtns.forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        
        // If mobile menu is open, close it first
        const mobileMenu = document.getElementById('mobile-menu');
        const navToggle = document.getElementById('nav-toggle');
        if (mobileMenu && mobileMenu.classList.contains('active')) {
          mobileMenu.classList.remove('active');
          if (navToggle) {
            const icon = navToggle.querySelector('md-icon');
            if (icon) icon.textContent = 'menu';
          }
        }
        
        openModal();
      });
    });

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => {
      if (e.target === modal) closeModal();
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        if (modal.classList.contains('active')) {
          closeModal();
        }
        if (dropdownToggleBtn && dropdownMenu && dropdownMenu.classList.contains('active')) {
          dropdownToggleBtn.setAttribute('aria-expanded', 'false');
          dropdownMenu.classList.remove('active');
        }
      }
    });

    // Populate fallback downloads list initially so clicking triggers actual files instantly
    const fallbackData = getFallbackData();
    populateDownloads(fallbackData);
    if (desktopList) {
      desktopList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">Checking preview builds… <a href="https://github.com/raulshma/JellyPlay/releases" target="_blank" rel="noopener noreferrer" class="inline-link">Browse releases</a></p>`;
    }

    // --- 3. Fetch Release Data asynchronously in background ---
    // Stable lane (/releases/latest) carries the Android APKs; the desktop
    // MSI/DEB/DMG installers ship on the KMP alpha pre-release channel, so
    // scan the recent releases (including pre-releases) for desktop assets.
    const cacheKey = 'jellyplay_latest_release';
    const desktopCacheKey = 'jellyplay_latest_desktop';
    const cacheDuration = 3600000; // 1 hour
    let releaseData = null;
    let desktopData = null;

    try {
      const cached = sessionStorage.getItem(cacheKey);
      if (cached) {
        const parsed = JSON.parse(cached);
        if (Date.now() - parsed.timestamp < cacheDuration) {
          releaseData = parsed.data;
        }
      }
    } catch (e) {
      console.warn('Failed to read release cache', e);
    }

    if (!releaseData) {
      try {
        const response = await fetch('https://api.github.com/repos/raulshma/JellyPlay/releases/latest');
        if (response.ok) {
          const data = await response.json();
          releaseData = {
            tag: data.tag_name,
            url: data.html_url,
            assets: data.assets.map(a => ({
              name: a.name,
              url: a.browser_download_url,
              size: a.size
            }))
          };
          // Save to cache
          try {
            sessionStorage.setItem(cacheKey, JSON.stringify({
              data: releaseData,
              timestamp: Date.now()
            }));
          } catch (e) {}
        }
      } catch (e) {
        console.error('Failed to fetch release from GitHub API', e);
      }
    }

    if (releaseData) {
      populateDownloads(releaseData);
    }

    // Desktop preview installers (alpha pre-release channel) — best effort.
    try {
      const cachedDesktop = sessionStorage.getItem(desktopCacheKey);
      if (cachedDesktop) {
        const parsed = JSON.parse(cachedDesktop);
        if (Date.now() - parsed.timestamp < cacheDuration) {
          desktopData = parsed.data;
        }
      }
    } catch (e) {
      console.warn('Failed to read desktop release cache', e);
    }

    if (!desktopData) {
      try {
        const response = await fetch('https://api.github.com/repos/raulshma/JellyPlay/releases?per_page=10');
        if (response.ok) {
          const releases = await response.json();
          for (const rel of releases) {
            const desktopAssets = (rel.assets || []).filter(a =>
              /\.(msi|exe|deb|rpm|dmg|zip)$/i.test(a.name) && /desktop/i.test(a.name)
            );
            if (desktopAssets.length) {
              desktopData = {
                tag: rel.tag_name,
                url: rel.html_url,
                assets: desktopAssets.map(a => ({
                  name: a.name,
                  url: a.browser_download_url,
                  size: a.size
                }))
              };
              try {
                sessionStorage.setItem(desktopCacheKey, JSON.stringify({
                  data: desktopData,
                  timestamp: Date.now()
                }));
              } catch (e) {}
              break;
            }
          }
        }
      } catch (e) {
        console.error('Failed to fetch desktop release from GitHub API', e);
      }
    }

    if (desktopData) {
      populateDesktopDownloads(desktopData);
    } else if (desktopList) {
      desktopList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No preview builds published yet — <a href="https://github.com/raulshma/JellyPlay/releases" target="_blank" rel="noopener noreferrer" class="inline-link">browse releases</a></p>`;
    }

    function populateDownloads(data) {
      navVersionEl.textContent = data.tag;
      modalVersionEl.textContent = data.tag;
      navBadge.style.display = 'inline-flex';
      if (data.url) navBadge.setAttribute('href', data.url);
      syncSoftwareVersion(data.tag);

      phoneList.innerHTML = '';
      tvList.innerHTML = '';
      if (dropdownList) dropdownList.innerHTML = '';

      let phoneCount = 0;
      let tvCount = 0;
      let dropdownCount = 0;
      let recommendedUrl = null;

      data.assets.forEach(asset => {
        if (asset.name.endsWith('.apk')) {
          const modalHtml = createDownloadItemHTML(asset);
          if (asset.name.includes('-phone-')) {
            phoneList.insertAdjacentHTML('beforeend', modalHtml);
            phoneCount++;
            if (!recommendedUrl && asset.name.includes('universal')) {
              recommendedUrl = asset.url;
            }
          } else if (asset.name.includes('-tv-')) {
            tvList.insertAdjacentHTML('beforeend', modalHtml);
            tvCount++;
          }

          if (dropdownList) {
            const dropdownHtml = createCompactDropdownItemHTML(asset);
            dropdownList.insertAdjacentHTML('beforeend', dropdownHtml);
            dropdownCount++;
          }
        }
      });

      if (!recommendedUrl) {
        const firstApk = data.assets.find(a => a.name.endsWith('.apk'));
        if (firstApk) recommendedUrl = firstApk.url;
      }
      if (recommendedUrl && heroDownloadLeft) {
        heroDownloadLeft.setAttribute('href', recommendedUrl);
      }

      if (phoneCount === 0) {
        phoneList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No phone APKs found.</p>`;
      }
      if (tvCount === 0) {
        tvList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No TV APKs found.</p>`;
      }
      if (dropdownCount === 0 && dropdownList) {
        dropdownList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No downloads found.</p>`;
      }
    }

    function populateDesktopDownloads(data) {
      if (!desktopList) return;
      desktopList.innerHTML = '';

      let count = 0;
      data.assets.forEach(asset => {
        desktopList.insertAdjacentHTML('beforeend', createDesktopItemHTML(asset, data));
        count++;
      });

      if (count === 0) {
        desktopList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No desktop builds in this release.</p>`;
      }
    }

    // Keep the SoftwareApplication structured data on the fetched version so
    // search results never go stale between site edits.
    function syncSoftwareVersion(tag) {
      try {
        const el = document.getElementById('ld-software');
        if (!el) return;
        const json = JSON.parse(el.textContent);
        json.softwareVersion = String(tag).replace(/^v/, '');
        el.textContent = JSON.stringify(json);
      } catch (e) {
        console.warn('Failed to sync structured-data version', e);
      }
    }
  }

  function formatBytes(bytes) {
    if (!bytes) return '';
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  }

  function createDownloadItemHTML(asset) {
    let archLabel = 'Universal';
    let archDesc = 'All devices';
    let isRecommended = false;
    
    if (asset.name.includes('universal')) {
      archLabel = 'Universal';
      archDesc = 'All architectures';
      isRecommended = true;
    } else if (asset.name.includes('arm64-v8a')) {
      archLabel = 'ARM64';
      archDesc = '64-bit phones & TV';
    } else if (asset.name.includes('x86_64')) {
      archLabel = 'x86_64';
      archDesc = 'Intel / Emulator';
    }

    const sizeStr = asset.size ? ` • ${formatBytes(asset.size)}` : '';
    const recText = isRecommended ? '<span class="download-item-name-rec">Recommended</span>' : '';
    
    return `
      <div class="download-item">
        <div class="download-item-info">
          <div class="download-item-name">
            ${archLabel}
            ${recText}
          </div>
          <div class="download-item-meta">${archDesc}${sizeStr}</div>
        </div>
        <a href="${asset.url}" class="download-item-btn" aria-label="Download ${archLabel} version" download>
          <span class="material-symbols-outlined">download</span>
        </a>
      </div>
    `;
  }

  function createCompactDropdownItemHTML(asset) {
    let deviceIcon = 'phone_android';
    let deviceTitle = 'Phone & Tablet Build';
    if (asset.name.includes('-tv-')) {
      deviceIcon = 'tv';
      deviceTitle = 'Android TV Build';
    }
    
    let archLabel = 'Universal';
    if (asset.name.includes('universal')) {
      archLabel = 'Universal';
    } else if (asset.name.includes('arm64-v8a')) {
      archLabel = 'ARM64';
    } else if (asset.name.includes('x86_64')) {
      archLabel = 'x86_64';
    }

    const sizeStr = asset.size ? formatBytes(asset.size) : 'APK';
    
    return `
      <a href="${asset.url}" class="compact-dropdown-item" download>
        <span class="dropdown-item-left">
          <md-icon class="device-icon" title="${deviceTitle}">${deviceIcon}</md-icon>
          <md-icon class="android-icon" title="Android Installer (APK)">android</md-icon>
          <span class="dropdown-item-title">${archLabel}</span>
        </span>
        <span class="dropdown-item-info">${sizeStr}</span>
      </a>
    `;
  }

  function createDesktopItemHTML(asset, release) {
    let platformLabel = 'Desktop';
    let platformDesc = asset.name;
    const lower = asset.name.toLowerCase();
    if (lower.includes('windows')) {
      platformLabel = 'Windows';
      platformDesc = lower.includes('.msi') ? 'Installer (MSI, x64)' : 'Windows build';
    } else if (lower.includes('linux')) {
      platformLabel = 'Linux';
      platformDesc = lower.includes('.deb') ? 'Debian package' : lower.includes('.rpm') ? 'RPM package' : 'Linux build';
    } else if (lower.includes('macos') || lower.endsWith('.dmg')) {
      platformLabel = 'macOS';
      platformDesc = 'Disk image (untested)';
    }

    const sizeStr = asset.size ? ` • ${formatBytes(asset.size)}` : '';
    const versionNote = release && release.tag ? ` • ${release.tag}` : '';

    return `
      <div class="download-item">
        <div class="download-item-info">
          <div class="download-item-name">
            ${platformLabel}
            <span class="download-item-name-rec">Preview</span>
          </div>
          <div class="download-item-meta">${platformDesc}${sizeStr}${versionNote}</div>
        </div>
        <a href="${asset.url}" class="download-item-btn" aria-label="Download ${platformLabel} preview build" download>
          <span class="material-symbols-outlined">download</span>
        </a>
      </div>
    `;
  }

  function getFallbackData() {
    const fallbackVersion = 'v0.10.7';
    const architectures = ['universal', 'arm64-v8a', 'x86_64'];
    const types = ['phone', 'tv'];
    const assets = [];
    
    types.forEach(type => {
      architectures.forEach(arch => {
        const filename = `jellyplay-${fallbackVersion}-${type}-${arch}.apk`;
        assets.push({
          name: filename,
          url: `https://github.com/raulshma/JellyPlay/releases/download/${fallbackVersion}/${filename}`,
          size: null
        });
      });
    });
    
    return {
      tag: fallbackVersion,
      url: `https://github.com/raulshma/JellyPlay/releases/tag/${fallbackVersion}`,
      assets: assets
    };
  }

  // ============ SPOTLIGHT GLOW EFFECT ============
  function initSpotlightGlow() {
    const cards = document.querySelectorAll('.feature-card');
    cards.forEach(card => {
      card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        card.style.setProperty('--mouse-x', `${x}px`);
        card.style.setProperty('--mouse-y', `${y}px`);
      });
    });
  }

  // ============ VIDEO ENGINE SWITCHER ============
  function initVideoEngineSwitcher() {
    const chips = document.querySelectorAll('.engine-chip');
    const badge = document.getElementById('player-engine-badge');
    const specs = document.getElementById('player-engine-specs');
    const progress = document.querySelector('.mock-player-progress-bar');
    
    if (!chips.length || !badge || !specs) return;
    
    const engineData = {
      exo: {
        name: 'ExoPlayer Engine',
        progress: '38%',
        specs: [
          '<span><md-icon>hdr_on</md-icon> HDR10+</span>',
          '<span><md-icon>picture_in_picture</md-icon> PiP Mode</span>',
          '<span><md-icon>gesture</md-icon> Gestures</span>',
          '<span><md-icon>speed</md-icon> Auto-Bitrate</span>'
        ]
      },
      mpv: {
        name: 'libmpv Engine',
        progress: '72%',
        specs: [
          '<span><md-icon>subtitles</md-icon> ASS Softsubs</span>',
          '<span><md-icon>fit_screen</md-icon> Custom Aspect</span>',
          '<span><md-icon>filter_b_and_w</md-icon> Video Filters</span>',
          '<span><md-icon>volume_up</md-icon> Audio Boost</span>'
        ]
      },
      vlc: {
        name: 'LibVLC Engine',
        progress: '15%',
        specs: [
          '<span><md-icon>cast</md-icon> Chromecast</span>',
          '<span><md-icon>lan</md-icon> Network Stream</span>',
          '<span><md-icon>music_video</md-icon> Disc ISO Play</span>',
          '<span><md-icon>slow_motion_video</md-icon> Pitch Correct</span>'
        ]
      }
    };
    
    chips.forEach(chip => {
      chip.addEventListener('click', () => {
        chips.forEach(c => {
          c.classList.remove('active');
          c.setAttribute('aria-selected', 'false');
        });
        chip.classList.add('active');
        chip.setAttribute('aria-selected', 'true');
        
        const engine = chip.dataset.engine;
        const data = engineData[engine];
        
        badge.textContent = data.name;
        if (progress) progress.style.width = data.progress;
        specs.innerHTML = data.specs.join('');
      });
    });
  }

  // ============ RICH AUDIO PLAYER SIMULATOR ============
  function initAudioSimulator() {
    const toggleBtn = document.getElementById('audio-play-toggle');
    const toggleIcon = document.getElementById('audio-play-icon');
    const vinyl = document.getElementById('audio-vinyl');
    const visualizer = document.getElementById('audio-visualizer');
    const lyricsWrapper = document.querySelector('.lyrics-wrapper');
    
    if (!toggleBtn || !vinyl || !visualizer || !lyricsWrapper) return;
    
    let isPlaying = false;
    let animationInterval = null;
    let lyricsInterval = null;
    let lyricIndex = 0;
    const bars = visualizer.querySelectorAll('.bar');
    
    const lyrics = [
      { text: "Midnight Stream is now playing...", active: true },
      { text: "Synchronized lyrics flowing in real-time", active: false },
      { text: "Ambient color blobs shift with the rhythm", active: false },
      { text: "Equalizer bands auto-tune to your mood", active: false }
    ];
    
    function updateVisualizer() {
      bars.forEach(bar => {
        const height = isPlaying ? Math.floor(Math.random() * 85) + 15 : 10;
        bar.style.height = `${height}%`;
      });
    }
    
    function updateLyrics() {
      lyricIndex = (lyricIndex + 1) % lyrics.length;
      
      lyricsWrapper.innerHTML = '';
      lyrics.forEach((lyric, idx) => {
        const p = document.createElement('p');
        p.className = 'lyric-line' + (idx === lyricIndex ? ' active' : '');
        p.textContent = lyric.text;
        lyricsWrapper.appendChild(p);
      });
      
      const offset = 12 - (lyricIndex * 24);
      lyricsWrapper.style.transform = `translateY(${offset}px)`;
    }
    
    toggleBtn.addEventListener('click', () => {
      isPlaying = !isPlaying;
      
      if (isPlaying) {
        toggleIcon.textContent = 'pause';
        vinyl.classList.add('playing');
        vinyl.classList.remove('paused');
        visualizer.classList.add('playing');
        
        animationInterval = setInterval(updateVisualizer, 150);
        lyricsInterval = setInterval(updateLyrics, 3000);
        updateLyrics();
      } else {
        toggleIcon.textContent = 'play_arrow';
        vinyl.classList.remove('playing');
        vinyl.classList.add('paused');
        visualizer.classList.remove('playing');
        
        clearInterval(animationInterval);
        clearInterval(lyricsInterval);
        
        bars.forEach(bar => bar.style.height = '10%');
      }
    });
  }

  // ============ ANDROID TV SIMULATOR ============
  function initTVSimulator() {
    const dpadUp = document.getElementById('dpad-up');
    const dpadDown = document.getElementById('dpad-down');
    const dpadLeft = document.getElementById('dpad-left');
    const dpadRight = document.getElementById('dpad-right');
    const dpadOk = document.getElementById('dpad-ok');
    const tvItems = document.querySelectorAll('.tv-item');
    const previewText = document.getElementById('tv-preview-text');
    
    if (!tvItems.length || !previewText) return;
    
    const items = Array.from(tvItems);
    let activeIndex = 0;
    
    const itemDescriptions = {
      movies: 'Browse your 4K Ultra HD movie library',
      tv: 'Stream multi-season TV shows with auto-next play',
      music: 'Listen to artists and custom audio playlists',
      settings: 'Customize display, playback engines, and styling'
    };
    
    function updateTVSelection(newIndex) {
      items[activeIndex].classList.remove('active');
      activeIndex = (newIndex + items.length) % items.length;
      items[activeIndex].classList.add('active');
      
      const key = items[activeIndex].dataset.tvItem;
      previewText.textContent = itemDescriptions[key] || '';
      previewText.style.animation = 'none';
      void previewText.offsetWidth;
      previewText.style.animation = 'fade-in 0.3s ease';
    }
    
    if (dpadUp) dpadUp.addEventListener('click', () => updateTVSelection(activeIndex - 2));
    if (dpadDown) dpadDown.addEventListener('click', () => updateTVSelection(activeIndex + 2));
    if (dpadLeft) dpadLeft.addEventListener('click', () => updateTVSelection(activeIndex - 1));
    if (dpadRight) dpadRight.addEventListener('click', () => updateTVSelection(activeIndex + 1));
    
    if (dpadOk) {
      dpadOk.addEventListener('click', () => {
        const key = items[activeIndex].dataset.tvItem;
        previewText.innerHTML = `<strong>Selected: ${key.toUpperCase()}</strong>`;
        setTimeout(() => {
          previewText.textContent = itemDescriptions[key];
        }, 1500);
      });
    }
    
    const tvCard = document.getElementById('feature-tv');
    if (tvCard) {
      tvCard.addEventListener('keydown', (e) => {
        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter'].includes(e.key)) {
          e.preventDefault();
          if (e.key === 'ArrowUp') updateTVSelection(activeIndex - 2);
          if (e.key === 'ArrowDown') updateTVSelection(activeIndex + 2);
          if (e.key === 'ArrowLeft') updateTVSelection(activeIndex - 1);
          if (e.key === 'ArrowRight') updateTVSelection(activeIndex + 1);
          if (e.key === 'Enter') dpadOk.click();
        }
      });
      tvCard.setAttribute('tabindex', '0');
    }
  }

  // ============ SYNCPLAY WATCH PARTY SIMULATOR ============
  function initSyncPlaySimulator() {
    const driftBtn = document.getElementById('syncplay-drift-btn');
    const badge = document.getElementById('syncplay-status-badge');
    const delayVal = document.getElementById('syncplay-delay-val');
    const progressAlex = document.getElementById('sync-progress-alex');
    const progressYou = document.getElementById('sync-progress-you');
    
    if (!driftBtn || !badge || !delayVal || !progressAlex || !progressYou) return;
    
    let isSimulating = false;
    
    driftBtn.addEventListener('click', () => {
      if (isSimulating) return;
      isSimulating = true;
      driftBtn.disabled = true;
      driftBtn.textContent = 'Simulating playback drift...';
      
      badge.textContent = 'DRIFT DETECTED';
      badge.className = 'syncplay-badge danger';
      delayVal.textContent = '420ms delay';
      progressYou.style.width = '55%';
      
      setTimeout(() => {
        badge.textContent = 'SYNCING (+5% speed)';
        badge.className = 'syncplay-badge warning';
        delayVal.textContent = '180ms delay';
        progressYou.style.width = '57%';
        
        setTimeout(() => {
          badge.textContent = 'IN SYNC';
          badge.className = 'syncplay-badge success';
          delayVal.textContent = '0ms delay';
          progressYou.style.width = '58%';
          driftBtn.disabled = false;
          driftBtn.textContent = 'Simulate Playback Drift';
          isSimulating = false;
        }, 2000);
      }, 2000);
    });
  }

  // ============ MINOR WIDGETS INTERACTIVE LOOP ============
  function initMinorWidgets() {
    const cpuVal = document.getElementById('admin-cpu-val');
    const adminCard = document.getElementById('feature-admin');
    if (cpuVal && adminCard) {
      setInterval(() => {
        const isUserHovering = adminCard.matches(':hover');
        if (isUserHovering) {
          const val = Math.floor(Math.random() * 30) + 15;
          cpuVal.textContent = `${val}%`;
        } else {
          const val = Math.floor(Math.random() * 8) + 12;
          cpuVal.textContent = `${val}%`;
        }
      }, 2500);
    }
    
    const dlCard = document.getElementById('feature-downloads');
    const dlPct = document.getElementById('dl-percentage');
    const dlBar = document.getElementById('dl-bar');
    if (dlCard && dlPct && dlBar) {
      dlCard.addEventListener('mouseenter', () => {
        let pct = 78;
        const interval = setInterval(() => {
          if (pct < 100) {
            pct += 1;
            dlPct.textContent = `${pct}%`;
            dlBar.style.width = `${pct}%`;
          } else {
            clearInterval(interval);
            setTimeout(() => {
              dlPct.textContent = '78%';
              dlBar.style.width = '78%';
            }, 2000);
          }
        }, 80);
        
        dlCard.addEventListener('mouseleave', () => {
          clearInterval(interval);
          dlPct.textContent = '78%';
          dlBar.style.width = '78%';
        }, { once: true });
      });
    }
  }

  // ============ THEME SHOWCASE (live style preview) ============
  function initThemeShowcase() {
    const row = document.getElementById('theme-showcase-row');
    const note = document.getElementById('theme-showcase-note');
    if (!row || !window.JellyPlayThemes) return;

    const chips = Array.from(row.querySelectorAll('[data-theme-variant]'));
    if (!chips.length) return;

    const labelFor = (id) => {
      const chip = chips.find((c) => c.dataset.themeVariant === id);
      return chip ? chip.textContent.trim() : id;
    };

    function syncActive() {
      const current = window.JellyPlayThemes.getCurrentVariant
        ? window.JellyPlayThemes.getCurrentVariant()
        : document.documentElement.getAttribute('data-variant');
      chips.forEach((c) => {
        const active = c.dataset.themeVariant === current;
        c.classList.toggle('active', active);
        c.setAttribute('aria-pressed', active ? 'true' : 'false');
      });
      if (note && current) {
        note.innerHTML = `You are previewing <strong></strong> — your choice is saved on this device only.`;
        note.querySelector('strong').textContent = labelFor(current);
      }
    }

    chips.forEach((chip) => {
      chip.addEventListener('click', () => {
        window.JellyPlayThemes.setVariant(chip.dataset.themeVariant);
        syncActive();
      });
    });

    // Stay in sync when the nav palette panel changes the style.
    const observer = new MutationObserver(syncActive);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-variant'] });
    syncActive();
  }

  // ============ FOOTER YEAR ============
  function initFooterYear() {
    const el = document.getElementById('footer-year');
    if (el) el.textContent = String(new Date().getFullYear());
  }

  // ============ INITIALIZE ============
  function init() {
    initScrollAnimations();
    initMobileNav();
    initNavScrollEffect();
    initShowcase();
    initFAQs();
    initSmoothScroll();
    initHeroParallax();
    initGithubStars();
    initReleaseAndDownloads();
    initSpotlightGlow();
    initVideoEngineSwitcher();
    initAudioSimulator();
    initTVSimulator();
    initSyncPlaySimulator();
    initMinorWidgets();
    initScrollSpy();
    initBackToTop();
    initThemeShowcase();
    initFooterYear();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
