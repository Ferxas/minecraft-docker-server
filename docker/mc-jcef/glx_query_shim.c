/*
 * JogAmp does: getProc = dlsym(libGLX, "glXGetProcAddress");
 *              fn = getProc("glXQueryDrawable");
 * So we must return OUR getProc from dlsym, not only interpose the symbol.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <string.h>

typedef void *(*dlsym_fn)(void *, const char *);
typedef void *(*get_proc_fn)(const unsigned char *);

static void stub_glXQueryDrawable(void *dpy, unsigned long draw, int attribute, unsigned int *value) {
  (void)dpy;
  (void)draw;
  (void)attribute;
  if (value) {
    *value = 0;
  }
}

static dlsym_fn real_dlsym(void) {
  static dlsym_fn real = NULL;
  if (!real) {
    real = (dlsym_fn)dlvsym(RTLD_NEXT, "dlsym", "GLIBC_2.34");
    if (!real) {
      real = (dlsym_fn)dlvsym(RTLD_NEXT, "dlsym", "GLIBC_2.2.5");
    }
  }
  return real;
}

static get_proc_fn real_get_proc(void) {
  static get_proc_fn real = NULL;
  if (!real) {
    dlsym_fn d = real_dlsym();
    if (d) {
      real = (get_proc_fn)d(RTLD_NEXT, "glXGetProcAddress");
      if (!real) {
        real = (get_proc_fn)d(RTLD_NEXT, "glXGetProcAddressARB");
      }
    }
  }
  return real;
}

void *glXGetProcAddress(const unsigned char *name) {
  if (name && strcmp((const char *)name, "glXQueryDrawable") == 0) {
    return (void *)&stub_glXQueryDrawable;
  }
  get_proc_fn real = real_get_proc();
  return real ? real(name) : NULL;
}

void *glXGetProcAddressARB(const unsigned char *name) {
  return glXGetProcAddress(name);
}

void glXQueryDrawable(void *dpy, unsigned long draw, int attribute, unsigned int *value) {
  stub_glXQueryDrawable(dpy, draw, attribute, value);
}

void *dlsym(void *handle, const char *symbol) {
  if (symbol) {
    if (strcmp(symbol, "glXQueryDrawable") == 0) {
      return (void *)&stub_glXQueryDrawable;
    }
    if (strcmp(symbol, "glXGetProcAddress") == 0) {
      return (void *)&glXGetProcAddress;
    }
    if (strcmp(symbol, "glXGetProcAddressARB") == 0) {
      return (void *)&glXGetProcAddressARB;
    }
  }
  dlsym_fn real = real_dlsym();
  return real ? real(handle, symbol) : NULL;
}
