-- Vídeo curto opcional no banner (MP4/H.264, até 4 MB e 15 s). A imagem continua obrigatória: ela aparece enquanto o vídeo
-- carrega, em rede móvel e para quem desligou as animações.
ALTER TABLE banner ADD COLUMN video BYTEA, ADD COLUMN duracao_video_ms INTEGER;
