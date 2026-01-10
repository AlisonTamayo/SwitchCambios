CREATE TABLE IF NOT EXISTS catalogoerror (
  codigo VARCHAR(10) PRIMARY KEY,
  descripcion TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS solicituddevolucion (
  id UUID PRIMARY KEY,
  idinstruccionoriginal UUID NOT NULL,
  codigomotivo VARCHAR(10) NOT NULL,
  estado VARCHAR(20) NOT NULL,
  fechasolicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_catalogo_devolucion
    FOREIGN KEY (codigomotivo) REFERENCES catalogoerror(codigo)
);
