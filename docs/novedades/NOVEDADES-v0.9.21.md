# 🎉 Novedades — Versión 0.9.21

(El apartado del SKU del taller se añade con su pieza.)

---

## 👀 Supertécnicos y administrador

### 🔎 IMEIs y Stock de teléfonos

- **Correcciones de NSYS:** cuando en NSYS se corrige un móvil dándolo de baja y de alta en otro lote (por ejemplo, tras
  cambiarle el chasis de color en el taller), el ERP lo trata como el mismo móvil:
  - en el rastro sale una sola línea, **«Corrección en NSYS (lote … → …)»**, con el móvil antes y después;
  - la ficha y la tabla del stock cuentan los días desde que entró el primer lote, y la ficha cuenta solo las entradas de
    verdad y conserva la revisión, la espera y la última caja de antes de la corrección.
